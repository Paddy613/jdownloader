//jDownloader - Downloadmanager
//Copyright (C) 2009  JD-Team support@jdownloader.org
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
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import jd.PluginWrapper;
import jd.config.Property;
import jd.http.Browser;
import jd.http.Cookies;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.Account;
import jd.plugins.Account.AccountType;
import jd.plugins.AccountInfo;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.plugins.components.SiteType.SiteTemplate;

import org.appwork.utils.formatter.SizeFormatter;
import org.appwork.utils.formatter.TimeFormatter;

@HostPlugin(revision = "$Revision: 36193 $", interfaceVersion = 2, names = { "sju.wang" }, urls = { "https?://(?:www\\.)?sju\\.wang/file\\-[a-z0-9]+\\.html" })
public class SjuWang extends PluginForHost {

    public SjuWang(PluginWrapper wrapper) {
        super(wrapper);
        // this.enablePremium("http://www.feemoo.com/upgrade.html");
    }

    @Override
    public String getAGBLink() {
        return "http://www.sju.wang/terms.html";
    }

    /* Connection stuff */
    private static final boolean FREE_RESUME                  = false;
    private static final int     FREE_MAXCHUNKS               = 1;
    private static final int     FREE_MAXDOWNLOADS            = 1;
    private static final boolean ACCOUNT_FREE_RESUME          = false;
    private static final int     ACCOUNT_FREE_MAXCHUNKS       = 1;
    private static final int     ACCOUNT_FREE_MAXDOWNLOADS    = 1;
    private static final boolean ACCOUNT_PREMIUM_RESUME       = false;
    private static final int     ACCOUNT_PREMIUM_MAXCHUNKS    = 1;
    private static final int     ACCOUNT_PREMIUM_MAXDOWNLOADS = 1;

    /* don't touch the following! */
    private static AtomicInteger maxPrem                      = new AtomicInteger(1);

    public void correctDownloadLink(final DownloadLink link) {
        link.setUrlDownload(link.getDownloadURL().replace("/vip/", "/file/"));
    }

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws IOException, PluginException {
        this.setBrowserExclusive();
        br.getPage(link.getDownloadURL());
        if (br.containsHTML("???????????????????????????????????????????????????????????????") || this.br.getHttpConnection().getResponseCode() == 404) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        final String filename = br.getRegex("<div class=\"span\\d+\">\\s*?<h1>([^<>\"]+)</h1>").getMatch(0);
        String filesize = br.getRegex("???????????????([^<>\"\\'\\&]+)\\&nbsp").getMatch(0);
        if (filename == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        /* Set final filename here because server filenames are bad. */
        link.setFinalFileName(Encoding.htmlDecode(filename.trim()));
        if (filesize != null) {
            filesize += "b";
            link.setDownloadSize(SizeFormatter.getSize(filesize));
        }
        return AvailableStatus.TRUE;
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception, PluginException {
        requestFileInformation(downloadLink);
        doFree(downloadLink, FREE_RESUME, FREE_MAXCHUNKS, "free_directlink");
    }

    private void doFree(final DownloadLink downloadLink, final boolean resumable, final int maxchunks, final String directlinkproperty) throws Exception, PluginException {
        final String fid = getFID(downloadLink);
        if (true) {
            /* 2017-02-21: Seems like free downloads are impossible. */
            throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_ONLY);
        }
        String dllink = checkDirectLink(downloadLink, directlinkproperty);
        if (dllink == null) {

            int wait = 30;
            final String waittime = br.getRegex("").getMatch(0);
            if (waittime != null) {
                wait = Integer.parseInt(waittime);
            }
            this.sleep(wait * 1001l, downloadLink);
            br.getPage("/down/" + fid + ".html");
            final String code = getCaptchaCode("/downcode.php", downloadLink);
            br.getHeaders().put("X-Requested-With", "XMLHttpRequest");
            br.postPage("/downcode.php", "action=yz&id=" + fid + "&code=" + Encoding.urlEncode(code));
            if (br.toString().equals("0")) {
                throw new PluginException(LinkStatus.ERROR_CAPTCHA);
            }
            br.getPage("/dd.php?file_key=" + fid + "&p=1");
            dllink = br.getRegex("").getMatch(0);
            if (dllink == null) {
                dllink = br.getRegex("").getMatch(0);
                if (dllink == null) {
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
            }
        }
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, dllink, resumable, maxchunks);
        if (dl.getConnection().getContentType().contains("html")) {
            br.followConnection();
            if (br.getURL().endsWith("/two.html")) {
                // this is when you're downloading too much or ip restriction (from google translate)
                // <div class="title">
                // <font color="#FF0000">???????????????????????????????????????????????????????????????????????????????????????????????????????????????</font>
                // </div>
                // <br />
                // <div class="content">
                // <div class="bottom"><br />
                // ????????????????????????????????????????????????????????????????????????????????????????????????
                // <br />1. ?????????????????????????????????????????????????????????????????????????????????????????????????????????
                // <br />
                // 2. ???????????????????????????????????????????????????????????????????????????????????????????????????????????????
                // <br /><br /><br />
                // <h1><a href="/pay_vip.php" target="_blank">?????????VIP????????????????????????</a></h1><br /><br /><br />
                // </div>
                // </div>
                // </div>
                throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, 20 * 60 * 60 * 1000l);
            }
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        downloadLink.setProperty(directlinkproperty, dllink);
        dl.startDownload();
    }

    private String checkDirectLink(final DownloadLink downloadLink, final String property) {
        String dllink = downloadLink.getStringProperty(property);
        if (dllink != null) {
            URLConnectionAdapter con = null;
            try {
                final Browser br2 = br.cloneBrowser();
                con = br2.openGetConnection(dllink);
                if (con.getContentType().contains("html") || con.getLongContentLength() == -1) {
                    downloadLink.setProperty(property, Property.NULL);
                    dllink = null;
                }
            } catch (final Exception e) {
                downloadLink.setProperty(property, Property.NULL);
                dllink = null;
            } finally {
                try {
                    con.disconnect();
                } catch (final Throwable e) {
                }
            }
        }
        return dllink;
    }

    private String getFID(final DownloadLink dl) {
        return new Regex(dl.getDownloadURL(), "([a-z0-9]+)\\.html$").getMatch(0);
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return FREE_MAXDOWNLOADS;
    }

    private static Object LOCK = new Object();

    @SuppressWarnings("deprecation")
    private void login(final Account account) throws Exception {
        synchronized (LOCK) {
            try {
                // Load cookies
                br.setCookiesExclusive(true);
                final Cookies cookies = account.loadCookies("");
                if (cookies != null) {
                    this.br.setCookies(this.getHost(), cookies);
                    br.getPage("http://" + account.getHoster() + "/member/");
                    if (this.br.containsHTML("/logout.php")) {
                        /* Save cookie timestamp. */
                        account.saveCookies(this.br.getCookies(this.getHost()), "");
                        return;
                    }
                    this.br = new Browser();
                }
                br.setFollowRedirects(false);
                br.getPage("http://www." + account.getHoster() + "/login.html");
                String postData = "type=login&nick=" + Encoding.urlEncode(account.getUser()) + "&pwd=" + Encoding.urlEncode(account.getPass());
                if (this.br.containsHTML("yzm\\.php")) {
                    final DownloadLink dummyLink = new DownloadLink(this, "Account", account.getHoster(), "http://" + account.getHoster(), true);
                    final String code = getCaptchaCode("/yzm.php", dummyLink);
                    postData += "&yzm=" + Encoding.urlEncode(code);
                }
                br.postPage("/post.php", postData);
                if (!br.toString().equals("1")) {
                    if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUng??ltiger Benutzername/Passwort oder login Captcha!\r\nSchnellhilfe: \r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen?\r\nFalls dein Passwort Sonderzeichen enth??lt, ??ndere es und versuche es erneut!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password or login captcha!\r\nQuick help:\r\nYou're sure that the username and password you entered are correct?\r\nIf your password contains special characters, change it (remove them) and try again!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                }
                account.saveCookies(this.br.getCookies(this.getHost()), "");
            } catch (final PluginException e) {
                account.clearCookies("");
                throw e;
            }
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        final AccountInfo ai = new AccountInfo();
        try {
            login(account);
        } catch (PluginException e) {
            account.setValid(false);
            throw e;
        }
        br.getPage("/member/userinfo.php");
        final String expire = br.getRegex(">???????????????</span><span class=\\\\mr15 w300 dib\">([^<>\"]*?)</span>").getMatch(0);
        ai.setUnlimitedTraffic();
        if (br.containsHTML("href=\"upvip\\.php\" title=\"?????????VIP??????\"") || expire == null) {
            account.setProperty("free", true);
            maxPrem.set(ACCOUNT_FREE_MAXDOWNLOADS);
            account.setType(AccountType.FREE);
            /* free accounts can still have captcha */
            account.setMaxSimultanDownloads(maxPrem.get());
            account.setConcurrentUsePossible(false);
            ai.setStatus("Registered (free) user");
        } else {
            account.setProperty("free", false);
            ai.setValidUntil(TimeFormatter.getMilliSeconds(expire, "yyyy-MM-dd", Locale.CHINA));
            maxPrem.set(ACCOUNT_PREMIUM_MAXDOWNLOADS);
            account.setType(AccountType.PREMIUM);
            account.setMaxSimultanDownloads(maxPrem.get());
            account.setConcurrentUsePossible(true);
            ai.setStatus("Premium Account");
        }
        account.setValid(true);
        return ai;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void handlePremium(final DownloadLink link, final Account account) throws Exception {
        requestFileInformation(link);
        login(account);
        br.setFollowRedirects(false);
        if (account.getBooleanProperty("free", false)) {
            br.getPage(link.getDownloadURL());
            doFree(link, ACCOUNT_FREE_RESUME, ACCOUNT_FREE_MAXCHUNKS, "account_free_directlink");
        } else {
            String dllink = this.checkDirectLink(link, "premium_directlink");
            if (dllink == null) {
                br.setFollowRedirects(true);
                br.getPage(link.getDownloadURL());
                dllink = br.getRegex("").getMatch(0);
                if (dllink == null) {
                    logger.warning("Final downloadlink (String is \"dllink\") regex didn't match!");
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
            }
            dl = jd.plugins.BrowserAdapter.openDownload(br, link, dllink, ACCOUNT_PREMIUM_RESUME, ACCOUNT_PREMIUM_MAXCHUNKS);
            if (dl.getConnection().getContentType().contains("html")) {
                logger.warning("The final dllink seems not to be a file!");
                br.followConnection();
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            link.setProperty("premium_directlink", dllink);
            dl.startDownload();
        }
    }

    @Override
    public int getMaxSimultanPremiumDownloadNum() {
        /* workaround for free/premium issue on stable 09581 */
        return maxPrem.get();
    }

    @Override
    public SiteTemplate siteTemplateType() {
        return SiteTemplate.Unknown_ChineseFileHosting;
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }

}