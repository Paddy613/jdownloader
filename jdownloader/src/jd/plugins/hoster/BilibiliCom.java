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
import jd.plugins.components.PluginJSonUtils;

@HostPlugin(revision = "$Revision: 34746 $", interfaceVersion = 3, names = { "bilibili.com" }, urls = { "http://www\\.bilibilidecrypted\\.com/video/av\\d+/index_\\d+\\.html" })
public class BilibiliCom extends PluginForHost {

    public BilibiliCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    /* DEV NOTES */
    // Tags:
    // protocol: no https
    // other:

    /* Connection stuff */
    private static final boolean free_resume       = true;
    private static final int     free_maxchunks    = 0;
    private static final int     free_maxdownloads = -1;

    private String               dllink            = null;
    private String               fid               = null;
    private String               part              = null;
    private boolean              server_issues     = false;

    @Override
    public String getAGBLink() {
        return "http://www.bilibili.com/";
    }

    public static Browser prepBR(final Browser br) {
        br.setAllowedResponseCodes(502);
        return br;
    }

    public void correctDownloadLink(final DownloadLink link) {
        final String vid = getFID(link);
        final String newurl = link.getDownloadURL().replace("bilibilidecrypted.com/", "bilibili.com/");
        link.setUrlDownload(newurl);
        link.setContentUrl(newurl);
    }

    private String getFID(final DownloadLink dl) {
        String fid = new Regex(dl.getDownloadURL(), "/av(\\d+)").getMatch(0);
        if (fid == null) {
            fid = new Regex(dl.getDownloadURL(), "/av(\\d+)").getMatch(0);
        }
        return new Regex(dl.getDownloadURL(), "/av(\\d+)").getMatch(0);
    }

    @SuppressWarnings("deprecation")
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws IOException, PluginException {
        prepBR(this.br);
        dllink = null;
        server_issues = false;
        fid = this.getFID(link);
        part = new Regex(link.getDownloadURL(), "index_(\\d+)").getMatch(0);
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        br.getPage(link.getDownloadURL());
        if (isOffline(this.br)) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        String filename = getTitle(this.br);
        if (filename == null) {
            filename = fid;
        }
        if (!filename.contains(part)) {
            filename += " - part_" + part;
        }
        filename = Encoding.htmlDecode(filename);
        filename = filename.trim();
        filename = encodeUnicode(filename);
        String ext = null;
        if (dllink != null) {
            ext = getFileNameExtensionFromString(dllink, ".flv");
        } else {
            ext = ".flv";
        }
        if (!filename.endsWith(ext)) {
            filename += ext;
        }
        if (dllink != null) {
            link.setFinalFileName(filename);
            final Browser br2 = br.cloneBrowser();
            // In case the link redirects to the finallink
            br2.setFollowRedirects(true);
            URLConnectionAdapter con = null;
            try {
                con = br2.openHeadConnection(dllink);
                if (!con.getContentType().contains("html")) {
                    link.setDownloadSize(con.getLongContentLength());
                    link.setProperty("directlink", dllink);
                } else {
                    server_issues = true;
                }
            } finally {
                try {
                    con.disconnect();
                } catch (final Throwable e) {
                }
            }
        } else {
            /* We cannot be sure whether we have the correct extension or not! */
            link.setName(filename);
        }
        return AvailableStatus.TRUE;
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception {
        requestFileInformation(downloadLink);
        /* Use their mobile function to avoid the encryption stuff of the PC-website! */
        this.br.getPage("/m/html5?aid=" + fid + "&page=1");
        dllink = PluginJSonUtils.getJsonValue(this.br, "src");
        if (dllink == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dllink = Encoding.htmlDecode(dllink);
        if (server_issues) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Unknown server error", 10 * 60 * 1000l);
        } else if (dllink == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, dllink, free_resume, free_maxchunks);
        if (dl.getConnection().getContentType().contains("html")) {
            if (dl.getConnection().getResponseCode() == 403) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 403", 60 * 60 * 1000l);
            } else if (dl.getConnection().getResponseCode() == 404) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 404", 60 * 60 * 1000l);
            }
            br.followConnection();
            try {
                dl.getConnection().disconnect();
            } catch (final Throwable e) {
            }
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl.startDownload();
    }

    public static boolean isOffline(final Browser br) {
        return br.getHttpConnection().getResponseCode() == 404 || br.getHttpConnection().getResponseCode() == 502 || br.containsHTML("????????????????????????????????????");
    }

    public static final String getTitle(final Browser br) {
        return br.getRegex("<title>([^<>]+)</title>").getMatch(0);
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return free_maxdownloads;
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
