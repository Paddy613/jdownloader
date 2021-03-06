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
import java.net.URLDecoder;
import java.util.Random;

import jd.PluginWrapper;
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

import org.appwork.utils.formatter.SizeFormatter;

@HostPlugin(revision = "$Revision: 34675 $", interfaceVersion = 2, names = { "worldclips.ru" }, urls = { "http://(www\\.)?(dev\\.)?worldclips\\.ru/clips/[^<>\"/]*?/[^<>\"/]+" }) 
public class WorldClipsRu extends PluginForHost {

    public WorldClipsRu(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium();
    }

    @Override
    public String getAGBLink() {
        return "http://worldclips.ru/info";
    }

    public void correctDownloadLink(DownloadLink link) {
        link.setUrlDownload(link.getDownloadURL().replace("dev.worldclips.ru/", "worldclips.ru/"));
    }

    private static final String TYPE_INVALID = "http://(www\\.)?worldclips\\.ru/clips/(top|new)/\\d+";

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws IOException, PluginException {
        this.setBrowserExclusive();
        if (link.getDownloadURL().matches(TYPE_INVALID)) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        br.getPage(link.getDownloadURL());
        if (br.containsHTML("<font size=\"8\">404</font><h2|> ?? ??????????????????, ?????????????????????????? ???????????????? ???? ??????????????<")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        final String off = br.getRegex("<tr class=\"off\">(.*?)</tr>").getMatch(0);
        if (off == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        final String[] rows = new Regex(off, "<td>([^<>\"]*?)</td>").getColumn(0);
        if (rows == null || rows.length == 0) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        final Regex urlFilename = new Regex(link.getDownloadURL(), "worldclips\\.ru/clips/([^<>\"/]*?)/([^<>\"/]+)");
        String filename = urlFilename.getMatch(0) + " - " + urlFilename.getMatch(1) + "." + rows[1];
        String filesize = rows[0];
        filesize = filesize.replace("??", "G");
        filesize = filesize.replace("??", "M");
        filesize = filesize.replaceAll("(??|??)", "k");
        filesize = filesize.replaceAll("(??|??)", "");
        filesize = filesize + "b";
        try {
            String charSet = br.getHttpConnection().getCharset();
            /* everything works with UTF-8, if you have different charset, then you have to use this than default one */
            filename = URLDecoder.decode(filename, charSet);
            link.setFinalFileName(filename.trim());
        } catch (final Throwable e) {
            link.setFinalFileName(Encoding.htmlDecode(filename.trim()));
        }
        link.setDownloadSize(SizeFormatter.getSize(filesize));
        return AvailableStatus.TRUE;
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception, PluginException {
        requestFileInformation(downloadLink);
        /**
         * Download is usually only for registered users but we can do it anyway
         */
        try {
            throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_ONLY);
        } catch (final Throwable e) {
            if (e instanceof PluginException) {
                throw (PluginException) e;
            }
        }
        throw new PluginException(LinkStatus.ERROR_FATAL, "This file is only available to premium members");
    }

    private static final String MAINPAGE = "http://worldclips.ru";
    private static Object       LOCK     = new Object();

    private void login(final Account account, final boolean force) throws Exception {
        synchronized (LOCK) {
            try {
                // Load cookies
                br.setCookiesExclusive(true);
                final Cookies cookies = account.loadCookies("");
                if (cookies != null && !force) {
                    this.br.setCookies(this.getHost(), cookies);
                    return;
                }
                br.setFollowRedirects(false);
                br.postPage("http://worldclips.ru/personal", "expire=on&auth=1&mail=" + Encoding.urlEncode(account.getUser()) + "&pass=" + Encoding.urlEncode(account.getPass()));
                if (br.getCookie(MAINPAGE, "member_hash") == null) {
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password!\r\nUng??ltiger Benutzername oder ung??ltiges Passwort!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                }
                account.saveCookies(this.br.getCookies(this.getHost()), "");
            } catch (final PluginException e) {
                account.clearCookies("");
                throw e;
            }
        }
    }

    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        final AccountInfo ai = new AccountInfo();
        if (!account.getUser().matches(".+@.+\\..+")) {
            if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nBitte gib deine E-Mail Adresse ins Benutzername Feld ein!", PluginException.VALUE_ID_PREMIUM_DISABLE);
            } else {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nPlease enter your e-mail adress in the username field!", PluginException.VALUE_ID_PREMIUM_DISABLE);
            }
        }
        try {
            login(account, true);
        } catch (PluginException e) {
            account.setValid(false);
            throw e;
        }
        ai.setUnlimitedTraffic();
        account.setValid(true);
        ai.setStatus("Free Account active");
        return ai;
    }

    @Override
    public void handlePremium(final DownloadLink link, final Account account) throws Exception {
        requestFileInformation(link);
        login(account, false);
        br.setFollowRedirects(false);
        br.getPage(link.getDownloadURL());
        final String clipID = getClipID();
        dl = jd.plugins.BrowserAdapter.openDownload(br, link, br.getURL(), "download_clip_id=" + clipID + "&x=" + new Random().nextInt(100) + "&y=" + new Random().nextInt(100), true, 1);
        if (dl.getConnection().getContentType().contains("html")) {
            logger.warning("The final dllink seems not to be a file!");
            br.followConnection();
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl.startDownload();
    }

    private String getClipID() throws PluginException {
        String clipID = br.getRegex("name=\"download_clip_id\" value=\"(\\d+)\"").getMatch(0);
        if (clipID == null) {
            clipID = br.getRegex("name=\"clip_id\" value=\"(\\d+)\"").getMatch(0);
        }
        if (clipID == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        return clipID;
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
        return -1;
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }

}