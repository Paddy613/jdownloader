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
import java.util.concurrent.atomic.AtomicInteger;

import jd.PluginWrapper;
import jd.nutils.encoding.Encoding;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;

import org.jdownloader.captcha.v2.challenge.recaptcha.v2.CaptchaHelperHostPluginRecaptchaV2;

@HostPlugin(revision = "$Revision: 36047 $", interfaceVersion = 3, names = { "akatsuki-subs.net" }, urls = { "http://(www\\.)?(downloads|archiv)\\.akatsuki\\-subs\\.net/file\\-\\d+\\.htm" })
public class AkatsukiSubsNet extends PluginForHost {

    public AkatsukiSubsNet(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public String getAGBLink() {
        return "http://akatsuki-subs.net/";
    }

    private static final String  SERVEROVERLOADED             = ">Server \\&uuml;berlastet\\!<";

    // note: CAN NOT be negative or zero! (ie. -1 or 0) Otherwise math sections fail. .:. use [1-20]
    private static AtomicInteger totalMaxSimultanFreeDownload = new AtomicInteger(20);
    // don't touch the following!
    private static AtomicInteger maxFree                      = new AtomicInteger(1);

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws IOException, PluginException {
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        try {
            br.setAllowedResponseCodes(503);
        } catch (final Throwable t) {
        }
        br.getHeaders().put("User-Agent", "Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/40.0.2214.93 Safari/537.36");
        br.getPage(link.getDownloadURL());
        if (br.containsHTML("Kein Download mit der angegebenen ID gefunden") || br.getHttpConnection().getResponseCode() == 503) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        if (br.containsHTML(SERVEROVERLOADED)) {
            link.getLinkStatus().setStatusText("Server overloaded");
            return AvailableStatus.UNCHECKABLE;
        }
        final String filename = br.getRegex(">Downloade: <span class=\"highlight\">([^<>\"]*?)</span>").getMatch(0);
        if (filename == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        link.setName(Encoding.htmlDecode(filename.trim()));
        final String md5 = br.getRegex("MD5: <span class=\"highlight\">([a-z0-9]{32})</span>").getMatch(0);
        if (md5 != null) {
            link.setMD5Hash(md5);
        }
        return AvailableStatus.TRUE;
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception, PluginException {
        requestFileInformation(downloadLink);
        // Limit on single link (IP)
        if (br.containsHTML(">Du hast das Limit an Downloads f??r diese Datei innerhalb von")) {
            throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, 60 * 60 * 1000l);
        }
        // Limit on all links (IP)
        if (br.containsHTML(">Du hast das Limit an Downloads innerhalb von")) {
            throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, 60 * 60 * 1000l);
        }
        // Server overloaded
        if (br.containsHTML(SERVEROVERLOADED)) {
            throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "Server overloaded", 5 * 60 * 1000l);
        }
        br.setFollowRedirects(false);
        final String recaptchaV2Response = new CaptchaHelperHostPluginRecaptchaV2(this, br).getToken();
        final String postData = "submit=Download&g-recaptcha-response=" + Encoding.urlEncode(recaptchaV2Response);
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, br.getURL(), postData, false, 1);
        if (dl.getConnection().getContentType().contains("html")) {
            br.followConnection();
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        if (dl.getConnection().getContentType().contains("html")) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        try {
            // add a download slot
            controlFree(+1);
            // start the dl
            dl.startDownload();
        } finally {
            // remove download slot
            controlFree(-1);
        }
    }

    /**
     * Prevents more than one free download from starting at a given time. One step prior to dl.startDownload(), it adds a slot to maxFree
     * which allows the next singleton download to start, or at least try.
     *
     * This is needed because xfileshare(website) only throws errors after a final dllink starts transferring or at a given step within pre
     * download sequence. But this template(XfileSharingProBasic) allows multiple slots(when available) to commence the download sequence,
     * this.setstartintival does not resolve this issue. Which results in x(20) captcha events all at once and only allows one download to
     * start. This prevents wasting peoples time and effort on captcha solving and|or wasting captcha trading credits. Users will experience
     * minimal harm to downloading as slots are freed up soon as current download begins.
     *
     * @param controlFree
     *            (+1|-1)
     */
    public synchronized void controlFree(final int num) {
        logger.info("maxFree was = " + maxFree.get());
        maxFree.set(Math.min(Math.max(1, maxFree.addAndGet(num)), totalMaxSimultanFreeDownload.get()));
        logger.info("maxFree now = " + maxFree.get());
    }

    @Override
    public void reset() {
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return maxFree.get();
    }

    @Override
    public void resetDownloadlink(final DownloadLink link) {
    }

    /* NO OVERRIDE!! We need to stay 0.9*compatible */
    public boolean hasCaptcha(DownloadLink link, jd.plugins.Account acc) {
        return true;
    }

    public boolean hasAutoCaptcha() {
        return false;
    }

}