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
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;

@HostPlugin(revision = "$Revision: 34675 $", interfaceVersion = 2, names = { "cestyksobe.cz" }, urls = { "http://(www\\.)?cestyksobe\\.cz/(novinky/novinky/\\d+|archiv/.*?/.*?)\\.html" }) 
public class CestyKsobeCz extends PluginForHost {

    private String DLLINK = null;

    public CestyKsobeCz(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public String getAGBLink() {
        return "http://cestyksobe.cz/kontakty.html";
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return -1;
    }

    @Override
    public void handleFree(DownloadLink downloadLink) throws Exception {
        requestFileInformation(downloadLink);
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, DLLINK, false, 1);
        if (dl.getConnection().getContentType().contains("html")) {
            br.followConnection();
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl.startDownload();
    }

    @Override
    public AvailableStatus requestFileInformation(DownloadLink downloadLink) throws IOException, PluginException {
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        br.setCustomCharset("utf-8");
        br.getPage(downloadLink.getDownloadURL());
        if (br.containsHTML("(>Hledan?? str??nka nebyla nalezena<|Omlouv??me se, ale V??mi po??adovan?? str??nka nebyla nalezena|Mo??n?? d??vody t??to chybov?? str??nky|>Po??adovan?? str??nka byla smaz??na ??i p??esunuta|>Zadal\\(a\\) jste ??patnou adresu)")) throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        String filename = br.getRegex("<title>(.*?)  \\| Cestyksobe\\.cz \\- </title>").getMatch(0);
        if (filename == null) {
            filename = br.getRegex("<h1 class=\"f\\-left\">(.*?)</h1>").getMatch(0);
            if (filename == null) filename = br.getRegex("<meta name=\"title\" content=\"(.*?) \\| Cestyksobe\\.cz \\- \"").getMatch(0);
        }
        DLLINK = br.getRegex("addVariable\\(\\'file\\',\\'(/.*?)\\'\\)").getMatch(0);
        if (DLLINK == null) DLLINK = br.getRegex("\\'(/include/playvideo\\.php\\?id_video=[a-z0-9]+)\\'").getMatch(0);
        if (filename == null || DLLINK == null) throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        DLLINK = "http://cestyksobe.cz" + Encoding.htmlDecode(DLLINK);
        filename = filename.trim();
        downloadLink.setFinalFileName(Encoding.htmlDecode(filename) + ".flv");
        Browser br2 = br.cloneBrowser();
        // In case the link redirects to the finallink
        br2.setFollowRedirects(true);
        URLConnectionAdapter con = null;
        try {
            con = br2.openGetConnection(DLLINK);
            if (!con.getContentType().contains("html"))
                downloadLink.setDownloadSize(con.getLongContentLength());
            else
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            return AvailableStatus.TRUE;
        } finally {
            try {
                con.disconnect();
            } catch (Throwable e) {
            }
        }
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }

    @Override
    public void resetPluginGlobals() {
    }
}