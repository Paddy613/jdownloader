//jDownloader - Downloadmanager
//Copyright (C) 2010  JD-Team support@jdownloader.org
//
//This program is free software: you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation, either version 3 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program.  If not, see <http://www.gnu.org/licenses/>.

package jd.plugins.hoster;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import jd.PluginWrapper;
import jd.config.Property;
import jd.http.Browser;
import jd.http.Cookie;
import jd.http.Cookies;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.Account;
import jd.plugins.AccountInfo;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.plugins.components.PluginJSonUtils;
import jd.plugins.components.UserAgents;
import jd.plugins.components.UserAgents.BrowserName;

import org.appwork.utils.formatter.SizeFormatter;

@HostPlugin(revision = "$Revision: 34718 $", interfaceVersion = 2, names = { "tropicshare.com" }, urls = { "http://(www\\.)?tropicshare\\.com/files/\\d+" })
public class TropicShareCom extends PluginForHost {

    public TropicShareCom(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium("http://tropicshare.com/users/premium");
    }

    @Override
    public String getAGBLink() {
        return "http://tropicshare.com/pages/6-Terms-of-service.html";
    }

    private static final AtomicReference<String> userAgent = new AtomicReference<String>(null);

    private void prepBR(final Browser br) {
        if (userAgent.get() == null) {
            userAgent.set(UserAgents.stringUserAgent(BrowserName.Chrome));
        }
        br.getHeaders().put("User-Agent", userAgent.get());
    }

    @SuppressWarnings("deprecation")
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws IOException, PluginException {
        this.setBrowserExclusive();
        prepBR(br);
        br.setFollowRedirects(true);
        br.getPage(link.getDownloadURL());
        if (br.containsHTML(">File size: </span>")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        final Regex fInfo = br.getRegex("<h2>([^<>\"]*?)<span style=\"float: right;font\\-size: 12px;\">File size: ([^<>\"]*?)</span>");
        final String filename = fInfo.getMatch(0);
        final String filesize = fInfo.getMatch(1);
        if (filename == null || filesize == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        link.setName(Encoding.htmlDecode(filename.trim()));
        link.setDownloadSize(SizeFormatter.getSize(filesize));
        return AvailableStatus.TRUE;
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception, PluginException {
        requestFileInformation(downloadLink);
        int wait = 60;
        final String regexedwait = br.getRegex("<count>(\\d+)</count").getMatch(0);
        if (regexedwait != null) {
            wait = Integer.parseInt(regexedwait);
        }
        final String fid = br.getRegex("fileid=\"(\\d+)\"").getMatch(0);
        if (fid == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        br.getHeaders().put("X-Requested-With", "XMLHttpRequest");
        br.postPage("/files/time/", "id=" + fid);
        if (br.containsHTML("\"status\":\"Please wait, while downloading\"")) {
            throw new PluginException(LinkStatus.ERROR_IP_BLOCKED);
        }
        final String uid = PluginJSonUtils.getJsonValue(br, "uid");
        if (uid == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        sleep(wait * 1001l, downloadLink);
        final String dllink = "/files/download/?uid=" + uid;
        dl = jd.plugins.BrowserAdapter.openDownload(this.br, downloadLink, dllink, false, 1);
        if (dl.getConnection().getContentType().contains("html")) {
            br.followConnection();
            if (br.containsHTML("Error, you not have premium acount")) {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_ONLY);
            } else if (br.containsHTML("Waiting time: ")) {
                final String minutes = br.getRegex("<span id=\"min\">(\\d+)</span>").getMatch(0);
                final String seconds = br.getRegex("<span id=\"sec\">(\\d+)</span>").getMatch(0);
                if (minutes != null && seconds != null) {
                    throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, Integer.parseInt(minutes) * 60 * 1001 + Integer.parseInt(seconds) * 1001l);
                }
                throw new PluginException(LinkStatus.ERROR_IP_BLOCKED);
            } else if (br.containsHTML("For parallel downloads please select one of Premium packages")) {
                throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "Wait before starting new downloads", 5 * 60 * 1000l);
            } else if (br.containsHTML("No htmlCode read")) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Unknown server error #1");
            }
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Unknown server error #2");
        }
        dl.startDownload();
    }

    private static final String MAINPAGE = "http://tropicshare.com";
    private static Object       LOCK     = new Object();

    @SuppressWarnings("unchecked")
    private void login(final Account account, final boolean force) throws Exception {
        synchronized (LOCK) {
            try {
                // Load cookies
                br.setCookiesExclusive(true);
                prepBR(br);
                final Object ret = account.getProperty("cookies", null);
                boolean acmatch = Encoding.urlEncode(account.getUser()).equals(account.getStringProperty("name", Encoding.urlEncode(account.getUser())));
                if (acmatch) {
                    acmatch = Encoding.urlEncode(account.getPass()).equals(account.getStringProperty("pass", Encoding.urlEncode(account.getPass())));
                }
                if (acmatch && ret != null && ret instanceof HashMap<?, ?> && !force) {
                    final HashMap<String, String> cookies = (HashMap<String, String>) ret;
                    if (account.isValid()) {
                        for (final Map.Entry<String, String> cookieEntry : cookies.entrySet()) {
                            final String key = cookieEntry.getKey();
                            final String value = cookieEntry.getValue();
                            br.setCookie(MAINPAGE, key, value);
                        }
                        return;
                    }
                }
                br.setFollowRedirects(false);
                br.postPage("http://tropicshare.com/users/login", "email=" + Encoding.urlEncode(account.getUser()) + "&password=" + Encoding.urlEncode(account.getPass()));
                if (br.getCookie(MAINPAGE, "login_sid") == null) {
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password!\r\nUng??ltiger Benutzername oder ung??ltiges Passwort!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                }
                // Save cookies
                final HashMap<String, String> cookies = new HashMap<String, String>();
                final Cookies add = this.br.getCookies(MAINPAGE);
                for (final Cookie c : add.getCookies()) {
                    cookies.put(c.getKey(), c.getValue());
                }
                account.setProperty("name", Encoding.urlEncode(account.getUser()));
                account.setProperty("pass", Encoding.urlEncode(account.getPass()));
                account.setProperty("cookies", cookies);
            } catch (final PluginException e) {
                account.setProperty("cookies", Property.NULL);
                throw e;
            }
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        AccountInfo ai = new AccountInfo();
        try {
            login(account, true);
        } catch (PluginException e) {
            account.setValid(false);
            throw e;
        }
        if (br.getURL() == null || !br.getURL().equals("http://tropicshare.com/")) {
            br.getPage("http://tropicshare.com/");
        }
        final Regex expireInfo = br.getRegex("Remain: <span>(\\d+)</span> Months <span>(\\d+)</span> Days");
        if (!br.containsHTML("<span>[\r\n\t ]+Premium") || expireInfo.getMatches().length != 1) {
            final String lang = System.getProperty("user.language");
            if ("de".equalsIgnoreCase(lang)) {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nNicht unterst??tzter Accounttyp!\r\nFalls du denkst diese Meldung sei falsch die Unterst??tzung dieses Account-Typs sich\r\ndeiner Meinung nach aus irgendeinem Grund lohnt,\r\nkontaktiere uns ??ber das support Forum.", PluginException.VALUE_ID_PREMIUM_DISABLE);
            } else {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUnsupported account type!\r\nIf you think this message is incorrect or it makes sense to add support for this account type\r\ncontact us via our support forum.", PluginException.VALUE_ID_PREMIUM_DISABLE);
            }
        }
        ai.setUnlimitedTraffic();
        final String months = expireInfo.getMatch(0);
        final String days = expireInfo.getMatch(1);
        final long monthsSeconds = Integer.parseInt(months) * 31 * 24 * 60 * 60;
        final long daysSeconds = Integer.parseInt(days) * 24 * 60 * 60;
        final long expireMilliseconds = (monthsSeconds + daysSeconds) * 1001;
        ai.setValidUntil(System.currentTimeMillis() + expireMilliseconds);
        account.setValid(true);
        ai.setStatus("Premium Account");
        return ai;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void handlePremium(final DownloadLink link, final Account account) throws Exception {
        requestFileInformation(link);
        br = new Browser();
        login(account, false);
        // can be directlinks!
        dl = jd.plugins.BrowserAdapter.openDownload(br, link, link.getDownloadURL(), true, -4);
        if (dl.getConnection().getContentType().contains("html")) {
            br.followConnection();
            final String fid = br.getRegex("fileid=\"(\\d+)\"").getMatch(0);
            if (fid == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            final String dllink = "/files/download/premium/" + fid;
            dl = jd.plugins.BrowserAdapter.openDownload(br, link, dllink, true, -4);
            if (dl.getConnection().getContentType().contains("html")) {
                logger.warning("The final dllink seems not to be a file!");
                br.followConnection();
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
        }
        dl.startDownload();
    }

    @Override
    public int getMaxSimultanPremiumDownloadNum() {
        return -1;
    }

    @Override
    public void reset() {
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return 1;
    }

    @Override
    public void resetDownloadlink(final DownloadLink link) {
    }

}