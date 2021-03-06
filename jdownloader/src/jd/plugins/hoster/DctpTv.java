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
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.plugins.components.PluginJSonUtils;

import org.jdownloader.downloader.hls.HLSDownloader;
import org.jdownloader.plugins.components.hls.HlsContainer;

@HostPlugin(revision = "$Revision: 36193 $", interfaceVersion = 3, names = { "dctp.tv" }, urls = { "https?://(?:www\\.)?dctp\\.tv/filme/[a-z0-9_\\-]+/" })
public class DctpTv extends PluginForHost {

    public DctpTv(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public String getAGBLink() {
        return "http://www.dctp.tv/";
    }

    /* Tags: spiegel.tv, dctp.tv */
    private static final boolean rtmpe_supported = false;
    private static final boolean prefer_hls      = true;
    private static final String  app             = "dctp/";

    @SuppressWarnings("deprecation")
    @Override
    public AvailableStatus requestFileInformation(DownloadLink link) throws IOException, PluginException {
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        br.getPage(link.getDownloadURL());
        if (br.containsHTML("class=\\'error\\'>Der gew??nschte Film ist \\(zur Zeit\\) nicht") || this.br.getHttpConnection().getResponseCode() == 404) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        String filename = br.getRegex("property=\"media:title\" content=\"([^<>\"]*?)\"></span>").getMatch(0);
        if (filename == null) {
            filename = br.getRegex("<meta name=\\'DC\\.title\\' content=\\'([^<>\"]*?)\\'").getMatch(0);
        }
        final String date = this.br.getRegex("name=\\'DC\\.date\\.created\\' content=\\'(\\d{4}\\-\\d{2}\\-\\d{2})\\'").getMatch(0);
        if (filename == null || date == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        filename = date + "_dctpTV_" + Encoding.htmlDecode(filename.trim()) + ".flv";
        link.setFinalFileName(filename);
        return AvailableStatus.TRUE;
    }

    @Override
    public void handleFree(DownloadLink downloadLink) throws Exception, PluginException {
        requestFileInformation(downloadLink);
        final String rtmpurl;
        if (rtmpe_supported) {
            rtmpurl = "rtmpe://dctpfs.fplive.net/" + app;
        } else {
            rtmpurl = "rtmp://dctpfs.fplive.net/" + app;
        }
        try {
            dl = new RTMPDownload(this, downloadLink, rtmpurl);
        } catch (final NoClassDefFoundError e) {
            throw new PluginException(LinkStatus.ERROR_FATAL, "RTMPDownload class missing");
        }
        /* Setup rtmp connection */
        jd.network.rtmp.url.RtmpUrlConnection rtmp = ((RTMPDownload) dl).getRtmpConnection();
        final String heigth = br.getRegex("height = (\\d+);").getMatch(0);
        String scalefactor;
        if (heigth != null && heigth.equals("180")) {
            scalefactor = "16x9";
        } else {
            scalefactor = "4x3";
        }

        String uuid = br.getRegex("name=\\'DC\\.identifier\\' content=\\'([^<>\"]+)\\'").getMatch(0);
        if (uuid == null) {
            uuid = br.getRegex("id=\\'uuid\\'>([^<>\"]*?)<").getMatch(0);
        }
        if (uuid == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }

        final String hls_playpath_path = this.br.getRegex("(/[^<>\"\\']+/playlist\\.m3u8)").getMatch(0);
        final String playpath = String.format("mp4:%s_dctp_0500_%s.m4v", uuid, scalefactor);
        String hls_stream_server = null;
        try {
            this.br.getPage("http://www.dctp.tv/elastic_streaming_client/get_streaming_server/");
            /* 2017-02-21: Usually '54.83.19.217' */
            hls_stream_server = PluginJSonUtils.getJsonValue(this.br, "server");
        } catch (final Throwable e) {
        }
        if (hls_stream_server != null && !hls_stream_server.equals("") && hls_playpath_path != null && prefer_hls) {
            final boolean build_custom_url = true;
            final String url_hls_master;
            if (build_custom_url) {
                url_hls_master = String.format("http://%s/vods3/_definst/mp4:dctp_completed_media/%s_dctp_0500_%s.m4v/playlist.m3u8", hls_stream_server, uuid, scalefactor);
            } else {
                url_hls_master = String.format("http://%s%s", playpath, hls_playpath_path);
            }
            if (url_hls_master == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            br.getPage(url_hls_master);
            final HlsContainer hlsbest = HlsContainer.findBestVideoByBandwidth(HlsContainer.getHlsQualities(this.br));
            final String url_hls = hlsbest.getDownloadurl();
            if (url_hls == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            checkFFmpeg(downloadLink, "Download a HLS Stream");
            dl = new HLSDownloader(downloadLink, br, url_hls);
            dl.startDownload();
        } else {
            rtmp.setPlayPath(playpath);
            // rtmp.setTcUrl("rtmp://mf.dctpvod.c.nmdn.net/dctpvod/");
            rtmp.setPageUrl(br.getURL());
            rtmp.setApp(app);
            rtmp.setFlashVer("WIN 19,0,0,185");
            rtmp.setSwfVfy("http://svm-prod-dctptv-static.s3.amazonaws.com/dctptv-relaunch2012-88.swf");
            rtmp.setUrl(rtmpurl);

            rtmp.setResume(true);
            ((RTMPDownload) dl).startDownload();
        }
    }

    public void setupRtmp(jd.network.rtmp.url.RtmpUrlConnection rtmp, final String clipuri) throws PluginException {
        final String playpathpart = br.getRegex("vods3/_definst/mp4:dctp_completed_media/([a-z0-9]{32})_iphone\\.m4v/playlist\\.m3u8\"").getMatch(0);
        if (playpathpart == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        final String heigth = br.getRegex("height = (\\d+);").getMatch(0);
        String aspect;
        if (heigth != null && heigth.equals("180")) {
            aspect = "16x9";
        } else {
            aspect = "4x3";
        }
        final String playpath = "mp4:" + playpathpart + "_dctp_0500_" + aspect + ".m4v";
        rtmp.setPlayPath(playpath);
        rtmp.setTcUrl("rtmp://mf.dctpvod.c.nmdn.net/dctpvod/");
        rtmp.setPageUrl(br.getURL());
        rtmp.setApp(app);
        rtmp.setFlashVer("WIN 16,0,0,235");
        rtmp.setSwfVfy("http://prod-dctptv-static.dctp.tv/dctptv-relaunch2012-63.swf");
        rtmp.setUrl(clipuri);
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