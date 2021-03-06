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
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;

import org.appwork.utils.formatter.SizeFormatter;

@HostPlugin(revision = "$Revision: 34675 $", interfaceVersion = 2, names = { "126disk.com" }, urls = { "http://(?:www\\.)?126(?:disk|xy)\\.com/(file|rf)view_\\d+\\.html" }) 
public class HundredTwentySixDiskCom extends PluginForHost {

    public HundredTwentySixDiskCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public String getAGBLink() {
        return "http://www.126disk.com/";
    }

    public void correctDownloadLink(DownloadLink link) {
        link.setUrlDownload(link.getDownloadURL().replace("rfview_", "fileview_"));
    }

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws IOException, PluginException {
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        br.getPage(link.getDownloadURL());
        if (br.getURL().endsWith("/error.php") || br.getHttpConnection().getResponseCode() == 403 || br.containsHTML(">??????????????????????????????????????????????????????|>\\s*???????????????????????????????????????\\s*<")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        String filename = br.getRegex("<h1><img src='[^<>\"]*?'>([^<>\"]*?)</h1>").getMatch(0);
        if (filename == null) {
            filename = br.getRegex("class=\"nowrap file-name( [a-z0-9\\-]+)?\">([^<>\"]*?)</h1>").getMatch(1);
        }
        String filesize = br.getRegex("<b>???????????? ???</b>([^<>\"]*?)</li>").getMatch(0);
        if (filesize == null) {
            filesize = br.getRegex("<table id=\"info_table\">[\t\n\r ]+<tr>[\t\n\r ]+<td width=\"160px;\">???????????????([^<>\"]*?)</td>").getMatch(0);
        }
        if (filename == null || filesize == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        link.setFinalFileName(Encoding.htmlDecode(filename.trim()));
        link.setDownloadSize(SizeFormatter.getSize(filesize + "b"));
        String md5 = br.getRegex(">M D 5??? ???</b>([a-z0-9]{32})</li>").getMatch(0);
        if (md5 == null) {
            md5 = br.getRegex("<td>??????MD5???([a-f0-9]{32})</td>").getMatch(0);
        }
        if (md5 != null) {
            link.setMD5Hash(md5);
        }
        return AvailableStatus.TRUE;
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception, PluginException {
        requestFileInformation(downloadLink);
        br.getPage("/download.php?id=" + new Regex(downloadLink.getDownloadURL(), "(\\d+)\\.html$").getMatch(0) + "&share=0&type=wt&t=" + System.currentTimeMillis());
        final String dllink = br.getRegex("\"(http://[a-z0-9]+\\.126(?:disk|xy)\\.com/[^<>\"]*?)\"").getMatch(0);
        if (dllink == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, Encoding.htmlDecode(dllink), false, 1);
        if (dl.getConnection().getContentType().contains("html")) {
            br.followConnection();
            if (br.containsHTML("<title>Error</title>|<TITLE>??????????????????</TITLE>")) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error");
            }
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl.startDownload();
    }

    @Override
    public void reset() {
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return -1;
    }

    @Override
    public void resetDownloadlink(final DownloadLink link) {
    }

}