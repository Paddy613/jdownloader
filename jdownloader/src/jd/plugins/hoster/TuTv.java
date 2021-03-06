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
import jd.parser.html.Form;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;

import org.jdownloader.controlling.filter.CompiledFiletypeFilter;

@HostPlugin(revision = "$Revision: 34727 $", interfaceVersion = 2, names = { "tu.tv" }, urls = { "http://(www\\.)?tu\\.tv/videos/[a-z0-9\\-_]+" })
public class TuTv extends PluginForHost {

    private String html_privatevideo = "class=\"videoprivado\"";
    private String DLLINK            = null;

    public TuTv(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public String getAGBLink() {
        // tu.tv belongs to hispavista.com
        return "http://hispavista.com/aviso/";
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return -1;
    }

    private Browser prepBR(final Browser br) {
        br.setAllowedResponseCodes(410);
        br.setFollowRedirects(true);
        return br;
    }

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink downloadLink) throws IOException, PluginException {
        this.setBrowserExclusive();
        prepBR(this.br);
        downloadLink.setMimeHint(CompiledFiletypeFilter.VideoExtensions.MP4);
        if (this.br.getURL() == null) {
            /* If user entered video password this requestFileInformation is called again - prevent double-accessing our downloadLink. */
            br.getPage(downloadLink.getDownloadURL());
        }
        final String url_filename = new Regex(downloadLink.getDownloadURL(), "/videos/([a-z0-9\\-_]+)").getMatch(0);
        if (this.br.getHttpConnection().getResponseCode() == 410) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        } else if (this.br.containsHTML("class=\"aviso_noexiste\"") || br.containsHTML("(>El v??deo que intentas ver no existe o ha sido borrado de TU|>Afortunadamente, el sistema ha encontrado v??deos relacionados con tu petici??n|>El v??deo no existe<)") || br.getURL().contains("/noExisteVideo/")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        } else if (this.br.containsHTML(html_privatevideo)) {
            logger.info("Private videos are not yet supported - contact our support so that we can add support for them");
            return AvailableStatus.TRUE;
        }
        String filename = br.getRegex("<title>([^<>\"]*?) \\- Tu\\.tv</title>").getMatch(0);
        if (filename == null) {
            filename = br.getRegex("class=\"title_comentario\">Comentarios de <strong>(.*?)</strong>").getMatch(0);
            if (filename == null) {
                filename = br.getRegex("<h2>(.*?)</h2>").getMatch(0);
            }
        }
        if (filename == null) {
            filename = br.getRegex("<meta name=\"title\" content=\"([^<>\"]*?)\"").getMatch(0);
        }
        if (filename == null) {
            /* Fallback to url-filename */
            filename = url_filename;
        }
        String vid = br.getRegex(">var codVideo=(\\d+);").getMatch(0);
        if (vid == null) {
            vid = br.getRegex("\\&xtp=(\\d+)\"").getMatch(0);
        }
        if (vid == null) {
            vid = br.getRegex("votoPlus\\((\\d+)\\);\"").getMatch(0);
        }
        if (vid == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        br.getPage("http://tu.tv/flvurl.php?codVideo=" + vid + "&v=WIN%2010,3,181,34&fm=1");
        DLLINK = br.getRegex("\\&kpt=([^<>\"]*?)\\&").getMatch(0);
        if (DLLINK == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        DLLINK = Encoding.Base64Decode(DLLINK);
        filename = filename.trim();
        downloadLink.setFinalFileName(filename + ".flv");
        final Browser br2 = br.cloneBrowser();
        // In case the link redirects to the finallink
        br2.setFollowRedirects(true);
        URLConnectionAdapter con = null;
        try {
            try {
                con = br2.openGetConnection(DLLINK);
            } catch (final Throwable e) {
                /* Let downloadcore handle issues - if this fails filesize won't be displayed to user which is not a major failure. */
                return AvailableStatus.TRUE;
            }
            if (!con.getContentType().contains("html")) {
                downloadLink.setDownloadSize(con.getLongContentLength());
            } else {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
        } finally {
            try {
                con.disconnect();
            } catch (Throwable e) {
            }
        }
        return AvailableStatus.TRUE;
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception {
        requestFileInformation(downloadLink);
        String passCode = downloadLink.getDownloadPassword();
        int counter = 0;
        Form passForm = null;
        while (this.br.containsHTML(html_privatevideo) && counter <= 2) {
            if (passCode == null || counter > 0) {
                passCode = getUserInput("Password?", downloadLink);
            }
            passForm = this.br.getFormbyKey("CODIGOVIDEO");
            if (passForm == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            passForm.put("CLAVE", Encoding.urlEncode(passCode));
            this.br.submitForm(passForm);
            counter++;
        }
        if (counter > 0 && this.br.containsHTML(html_privatevideo)) {
            throw new PluginException(LinkStatus.ERROR_RETRY, "Wrong password entered");
        } else if (counter > 0) {
            /* User entered correct password --> Call requestFileInformation again to find filename and downloadurl. */
            requestFileInformation(downloadLink);
            downloadLink.setDownloadPassword(passCode);
        }
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, DLLINK, true, 0);
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

    @Override
    public void resetPluginGlobals() {
    }
}