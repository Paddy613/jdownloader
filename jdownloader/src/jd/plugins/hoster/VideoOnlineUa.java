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

import jd.PluginWrapper;
import jd.http.Browser;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;

@HostPlugin(revision = "$Revision: 34711 $", interfaceVersion = 2, names = { "video.online.ua" }, urls = { "http://video\\.online\\.uadecrypted/\\d+" })
public class VideoOnlineUa extends PluginForHost {

    public VideoOnlineUa(PluginWrapper wrapper) {
        super(wrapper);
    }

    private String dllink = null;

    @Override
    public String getAGBLink() {
        return "http://about.online.ua/contact/";
    }

    public void correctDownloadLink(DownloadLink link) {
        link.setUrlDownload(link.getDownloadURL().replace("video.online.uadecrypted/", "video.online.ua/"));
    }

    @SuppressWarnings("deprecation")
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink downloadLink) throws IOException, PluginException {
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        // Allow adult videos
        br.setCookie("http://video.online.ua/", "online_18", "1");
        br.getPage(downloadLink.getDownloadURL());
        String externLink = br.getRegex("\"(http://(www\\.)?novy\\.tv/embed/\\d+)\"").getMatch(0);
        if (externLink == null) {
            externLink = br.getRegex("\"(http://(www\\.)?rutube\\.ru/video/embed/\\d+)\"").getMatch(0);
        }
        // External sites - not supported
        if (externLink != null || br.containsHTML("ictv\\.ua/public/swfobject/zl_player\\.swf\"")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        final String fid = new Regex(downloadLink.getDownloadURL(), "(\\d+)$").getMatch(0);
        br.getPage("http://video.online.ua/embed/" + fid);
        if (br.containsHTML(">???????????????? ???? ?????????????? ???????????? ??????????????????????<")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        // External sites - their videos don't even play via browser
        if (br.containsHTML("stb\\.ua/embed/|m1\\.tv/")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }

        String filename = br.getRegex("<title>([^<>\"]*?)</title>").getMatch(0);
        final String hash = br.getRegex("\"hash\":\"([^<>\"]*?)\"").getMatch(0);
        if (hash != null) {
            dllink = "http://video.online.ua/playlist/" + fid + ".xml?o=t&" + hash;
        } else {
            dllink = br.getRegex("file: \\'(http://video\\.online\\.ua/playlist/\\d+\\.xml[^<>\"]*?)\\'").getMatch(0);
            if (dllink == null) {
                dllink = br.getRegex("video\\s*?:\\s*?(?:\\'|\")(https?://[^<>\"\\']+)(?:\\'|\")").getMatch(0);
            }
        }
        if (filename == null || dllink == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        final Browser br2 = br.cloneBrowser();
        // In case the link redirects to the finallink
        br2.setFollowRedirects(true);
        URLConnectionAdapter con = null;
        try {
            con = br2.openHeadConnection(dllink);
            if (con.getResponseCode() == 404) {
                /* Offline - video is officially available but will fail to play via browser as well! */
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            if (con.getContentType().contains("html")) {
                br2.getPage(dllink);
                if (br.containsHTML(">???????????????? ???? ?????????????? ???????????? ??????????????????????<")) {
                    throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                }
                dllink = br.getRegex("<location>(http://[^<>\"]*?)</location>").getMatch(0);
                if (dllink == null) {
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                dllink = Encoding.htmlDecode(dllink);
                con = br2.openHeadConnection(dllink);
            }
            if (!con.getContentType().contains("html")) {
                downloadLink.setDownloadSize(con.getLongContentLength());
            } else {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            filename = Encoding.htmlDecode(filename).trim();
            final String ext = getFileNameExtensionFromString(dllink, ".mp4");
            downloadLink.setFinalFileName(filename + ext);
            return AvailableStatus.TRUE;
        } finally {
            try {
                con.disconnect();
            } catch (Throwable e) {
            }
        }
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception {
        requestFileInformation(downloadLink);
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, dllink, true, 0);
        if (dl.getConnection().getContentType().contains("html")) {
            br.followConnection();
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl.startDownload();
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return -1;
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetPluginGlobals() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }
}
