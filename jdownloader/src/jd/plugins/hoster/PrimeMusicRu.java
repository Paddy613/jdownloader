//    jDownloader - Downloadmanager
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

import jd.PluginWrapper;
import jd.nutils.encoding.Encoding;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;

import org.appwork.utils.formatter.SizeFormatter;
import org.jdownloader.plugins.components.antiDDoSForHost;

@HostPlugin(revision = "$Revision: 35634 $", interfaceVersion = 2, names = { "primemusic.ru" }, urls = { "http://(www\\.)?(primemusic\\.ru|prime\\-music\\.net|primemusic\\.cc)/Media\\-page\\-\\d+\\.html" })
public class PrimeMusicRu extends antiDDoSForHost {

    @Override
    public String[] siteSupportedNames() {
        return new String[] { "primemusic.ru", "prime-music.net", "primemusic.cc" };
    }

    public PrimeMusicRu(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public String getAGBLink() {
        return "http://primemusic.cc";
    }

    @SuppressWarnings("deprecation")
    public void correctDownloadLink(final DownloadLink link) {
        link.setUrlDownload(link.getDownloadURL().replaceAll("(primemusic\\.ru|prime-music\\.net)/", "primemusic.cc/"));
    }

    @SuppressWarnings("deprecation")
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws Exception {
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        getPage(link.getDownloadURL());
        if (br.containsHTML("<h1 class=\"radio_title\">???????????????????? ???? ??????????????</h1>|>???????????????????? ??????????????")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        String finalfilename = br.getRegex("<h2>?????????????? ([^<>\"]*?)\\.mp3</h2>").getMatch(0);
        if (finalfilename == null) {
            finalfilename = br.getRegex("<div class=\"caption\">[\t\n\r ]+<h1>([^<>\"]*?) ?????????????? ??????????</h1>").getMatch(0);
        }
        String filesize = br.getRegex("<b>????????????:?</b>:?([^<>\"]*?)</span>").getMatch(0);
        if (finalfilename == null || filesize == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        link.setFinalFileName(Encoding.htmlDecode(finalfilename.trim()) + ".mp3");
        link.setDownloadSize(SizeFormatter.getSize(filesize.replace(",", ".")));
        return AvailableStatus.TRUE;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception, PluginException {
        requestFileInformation(downloadLink);
        br.setFollowRedirects(false);
        getPage(downloadLink.getDownloadURL().replace("/Media-page-", "/Media-download-"));
        String finallink = br.getRedirectLocation();
        if (finallink == null) {
            br.getRegex("<a class=\"download\" href=(http://[^<>\"]*?\\.mp3)\"").getMatch(0);
            if (finallink == null) {
                finallink = br.getRegex("class=\"download_link\" href=\"(https?://[^<>\"]*?)\"").getMatch(0);
                if (finallink == null) {
                    finallink = br.getRegex("\"(http://[a-z0-9]+\\.(primemusic\\.ru|prime\\-music\\.net|primemusic\\.cc)/dl\\d+/[^<>\"]*?)\"").getMatch(0);
                    if (finallink == null) {
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                }
            }
        }
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, finallink, true, 0);
        if (dl.getConnection().getContentType().contains("html")) {
            br.followConnection();
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
    public void resetDownloadlink(DownloadLink link) {
    }

}