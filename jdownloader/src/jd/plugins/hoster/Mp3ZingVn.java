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

/**
 * @author noone2407
 */
package jd.plugins.hoster;

import java.util.LinkedHashMap;

import org.jdownloader.scripting.JavaScriptEngineFactory;

import jd.PluginWrapper;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;

/**
 * @author noone2407
 * @author raztoki
 *
 */
@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "mp3.zing.vn" }, urls = { "http://mp3\\.zing\\.vn/bai-hat/(\\S+)\\.html$" })
public class Mp3ZingVn extends PluginForHost {

    private String dllink = null;

    public Mp3ZingVn(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public String getAGBLink() {
        return "http://mp3.zing.vn/huong-dan/terms";
    }

    @Override
    public AvailableStatus requestFileInformation(DownloadLink downloadLink) throws Exception {
        this.setBrowserExclusive();
        String url = downloadLink.getDownloadURL();
        br.setFollowRedirects(true);
        br.getPage(url);
        String filename = br.getRegex("<s class=\"fn-name\">(.*?)<\\/s>").getMatch(0);
        if (filename == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        downloadLink.setFinalFileName(filename.replaceFirst("\\.{3}$", "").replace(":", "-") + ".mp3");
        final String datacode = br.getRegex("<a\\s+(?:[^>]*?\\s+)?data-code=\"(.*?)\"").getMatch(0);
        final String json_source = br.getPage("http://mp3.zing.vn/xhr/song/get-download?panel=.fn-tab-panel-service&code=" + datacode + "&group=.fn-tab-panel");
        final LinkedHashMap<String, Object> entries = (LinkedHashMap<String, Object>) JavaScriptEngineFactory.jsonToJavaObject(json_source);
        if ("Kh??ng th??? download b??i h??t n??y v?? y??u c???u t??? nh?? s??? h???u b???n quy???n.".equals(entries.get("msg"))) {
            // from google translate
            // Unable to download this song because the request from the copyright owner.
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        final String datalink = (String) JavaScriptEngineFactory.walkJson(entries, "data/128/link");
        final Integer datasize = (Integer) JavaScriptEngineFactory.walkJson(entries, "data/128/size");
        final Boolean vip = (Boolean) JavaScriptEngineFactory.walkJson(entries, "data/128/vip");
        dllink = datalink;
        downloadLink.setDownloadSize(datasize);
        if (Boolean.TRUE.equals(vip)) {
            throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_ONLY);
        }

        return AvailableStatus.TRUE;
    }

    @Override
    public void handleFree(DownloadLink downloadLink) throws Exception {
        requestFileInformation(downloadLink);
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, dllink, true, 0);
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
    public void resetDownloadlink(DownloadLink link) {
    }

}
