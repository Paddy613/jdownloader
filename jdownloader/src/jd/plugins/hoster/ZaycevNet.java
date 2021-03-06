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

import jd.PluginWrapper;
import jd.config.Property;
import jd.http.Browser;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;

import org.appwork.utils.formatter.SizeFormatter;

@HostPlugin(revision = "$Revision: 34675 $", interfaceVersion = 2, names = { "zaycev.net" }, urls = { "http://((www\\.)?zaycev\\.net/pages/[0-9]+/[0-9]+\\.shtml|dl\\.zaycev\\.net/[^\r\n\"]+)" }) 
public class ZaycevNet extends PluginForHost {

    public ZaycevNet(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public String getAGBLink() {
        return "http://zaycev.net/";
    }

    private static final String CAPTCHATEXT = "/captcha/";
    private String              finallink   = null;

    @SuppressWarnings("deprecation")
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws IOException, PluginException {
        this.setBrowserExclusive();
        br.getHeaders().put("Accept-Charset", null);
        br.getHeaders().put("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/38.0.2125.101 Safari/537.36");
        this.br.setAllowedResponseCodes(410);
        br.setFollowRedirects(false);
        if (link.getDownloadURL().matches(".+dl\\.zaycev\\.net/.+")) {
            finallink = link.getDownloadURL();
            return AvailableStatus.TRUE;
        }
        br.setCookie(this.getHost(), "mm_cookie", "1");
        br.getPage(link.getDownloadURL());
        final int responsecode = this.br.getHttpConnection().getResponseCode();
        if (br.getRedirectLocation() != null || br.containsHTML("http\\-equiv=\"Refresh\"|>???????????? ???????????????????? ??????????????????????????, ???????????????? ??????????????????") || responsecode == 404 || responsecode == 410) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        String filename = br.getRegex("<span itemprop=\"name\">(.*?)\\s*<link").getMatch(0);
        if (filename == null) {
            filename = br.getRegex("id=\"pages\\-download\\-link\">([^<>\"]*?)</a>").getMatch(0);
        }
        if (filename == null) {
            filename = br.getRegex("class=\"download\\-title__text\">([^<>\"]*?)</span>").getMatch(0);
        }
        final String filesize = br.getRegex("??<meta content=\"(.*?)\" itemprop=\"contentSize\"/>").getMatch(0);
        if (filename == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        link.setFinalFileName(Encoding.htmlDecode(filename.trim().replaceAll("</?span>", "")) + ".mp3");
        link.setDownloadSize(SizeFormatter.getSize(filesize));
        return AvailableStatus.TRUE;
    }

    @Override
    public void handleFree(DownloadLink downloadLink) throws Exception, PluginException {
        br = new Browser();
        finallink = null;
        requestFileInformation(downloadLink);
        // if (finallink == null) {
        // finallink = checkDirectLink(downloadLink, "savedlink");
        if (finallink == null) {
            finallink = br.getRegex("\"(http://dl\\.zaycev\\.net/[^<>\"]*?)\"").getMatch(0);
            if (finallink == null) {
                String cryptedlink = br.getRegex("\"(/download\\.php\\?id=\\d+\\&ass=[^<>/\"]*?\\.mp3)\"").getMatch(0);
                if (cryptedlink == null) {
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                br.getPage(cryptedlink);
                finallink = getDllink();
                if (finallink == null) {
                    if (br.containsHTML(CAPTCHATEXT)) {
                        for (int i = 0; i <= 5; i++) {
                            // Captcha handling
                            String captchaID = getCaptchaID();
                            if (captchaID == null) {
                                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                            }
                            String code = getCaptchaCode("/captcha/" + captchaID + "/", downloadLink);
                            String captchapage = cryptedlink + "&captchaId=" + captchaID + "&text_check=" + code + "&ok=%F1%EA%E0%F7%E0%F2%FC";
                            br.getPage(captchapage);
                            if (br.containsHTML(CAPTCHATEXT)) {
                                continue;
                            }
                            break;
                        }
                        if (br.containsHTML(CAPTCHATEXT)) {
                            throw new PluginException(LinkStatus.ERROR_CAPTCHA);
                        }
                    } else {
                        String code = br.getRegex("<label>?????? IP</label><span class=\"readonly\">[0-9\\.]+</span></div><input value=\"(.*?)\"").getMatch(0);
                        String captchaID = getCaptchaID();
                        if (code == null || captchaID == null) {
                            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                        }
                        String captchapage = cryptedlink + "&captchaId=" + captchaID + "&text_check=" + code + "&ok=%F1%EA%E0%F7%E0%F2%FC";
                        br.getPage(captchapage);
                    }
                    finallink = getDllink();
                }
            }
        }

        // Since I fixed the download core setting correct redirect referrer I can no longer use redirect header to determine error code for
        // max connections. This is really only a problem with media files as filefactory redirects to /stream/ directly after code=\d+
        // which breaks our generic handling. This will fix it!! - raztoki
        int i = -1;
        br.setFollowRedirects(false);
        URLConnectionAdapter con = null;
        int repeatTries = 20;
        while (i++ < repeatTries) {
            try {
                try {
                    // @since JD2
                    con = br.openHeadConnection(finallink);
                } catch (final Throwable t) {
                    con = br.openGetConnection(finallink);
                }
                final String r = br.getRedirectLocation();
                final boolean cd = con.isContentDisposition();
                final boolean ct = con.getContentType().contains("audio/");
                if ((!cd || !ct) && r != null) {
                    if (i + 1 >= repeatTries) {
                        throw new PluginException(LinkStatus.ERROR_FATAL, "RedirectLoop");
                    }
                    // redirect, we want to store and continue down the rabbit hole!
                    finallink = r;
                    sleep(5000l * i, downloadLink);
                    continue;
                } else {
                    // finallink! (usually doesn't container redirects)
                    finallink = br.getURL();
                    break;
                }
            } finally {
                try {
                    con.disconnect();
                } catch (Throwable e) {
                }
            }
        }
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, finallink, true, 0);
        if (dl.getConnection().getContentType().contains("html")) {
            br.followConnection();
            if (br.containsHTML(">In this country site zaycev\\.net is not accessable\\.<")) {
                throw new PluginException(LinkStatus.ERROR_FATAL, "Download blocked by hoster with Geolocation filter!");
            }
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        // id3 and packagename can be tag due to directlink imports
        // downloadLink.setFinalFileName(getFileNameFromHeader(dl.getConnection()).replaceAll("_?\\(zaycev\\.net\\)", ""));
        downloadLink.setProperty("savedlink", finallink);
        dl.startDownload();
    }

    private String getCaptchaID() {
        String captchaID = br.getRegex("name=\"id\" type=\"hidden\"/><input value=\"(\\d+)\"").getMatch(0);
        if (captchaID == null) {
            captchaID = br.getRegex("\"/captcha/(\\d+)").getMatch(0);
        }
        return captchaID;
    }

    private String checkDirectLink(DownloadLink downloadLink, String property) {
        String dllink = downloadLink.getStringProperty(property);
        if (dllink != null) {
            try {
                Browser br2 = br.cloneBrowser();
                URLConnectionAdapter con = br2.openGetConnection(dllink);
                if (con.getContentType().contains("html") || con.getLongContentLength() == -1) {
                    downloadLink.setProperty(property, Property.NULL);
                    dllink = null;
                }
                con.disconnect();
            } catch (Exception e) {
                downloadLink.setProperty(property, Property.NULL);
                dllink = null;
            }
        }
        return dllink;
    }

    private String getDllink() {
        String finallink = br.getRegex("\\{REFRESH: \\{url: \"(http://dl\\.zaycev\\.net/[^<>\"]*?)\"").getMatch(0);
        if (finallink == null) {
            finallink = br.getRegex("???? ?????????????? ???? ?????? <a href=\\'(http.*?)\\'").getMatch(0);
            if (finallink == null) {
                finallink = br.getRegex("\"(http://dl\\.zaycev\\.net/[a-z0-9\\-]+/\\d+/\\d+/.*?)\"").getMatch(0);
            }
        }
        return finallink;
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

    /* NO OVERRIDE!! We need to stay 0.9*compatible */
    public boolean hasCaptcha(DownloadLink link, jd.plugins.Account acc) {
        return true;
    }
}