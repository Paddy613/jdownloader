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

package jd.plugins.decrypter;

import java.io.File;
import java.util.ArrayList;
import java.util.Random;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterException;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.PluginForDecrypt;

import org.jdownloader.captcha.v2.challenge.recaptcha.v1.Recaptcha;

@DecrypterPlugin(revision = "$Revision: 34675 $", interfaceVersion = 2, names = { "vosprotects.com" }, urls = { "http://(www\\.)?vosprotects\\.com/(linkcheck|linkidwoc)\\.php\\?linkid=[a-z0-9]+" }) 
public class VosProtectsCom extends PluginForDecrypt {

    public VosProtectsCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    /* DecrypterScript_linkid=_linkcheck.php Version 0.1 */

    private static final String  RECAPTCHATEXT  = "api\\.recaptcha\\.net";
    private static final String  RECAPTCHATEXT2 = "google\\.com/recaptcha/api/challenge";
    private static final boolean SKIP_CAPTCHA   = true;

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        final String parameter = param.toString().replace("linkidwoc.php", "linkcheck.php");
        final String domain = new Regex(parameter, "http://(www\\.)?([^<>\"/]*?)/").getMatch(1);
        final String linkid = new Regex(parameter, "([a-z0-9]+)$").getMatch(0);
        br.setFollowRedirects(true);
        br.setCustomCharset("utf-8");
        br.getPage(parameter);
        /* Prefer reCaptcha */
        br.getPage(parameter + "&c=1");
        final String rcChallenge = br.getRegex("recaptcha/api/challenge\\?k=([^<>\"]*?)\"").getMatch(0);

        if (!SKIP_CAPTCHA) {
            boolean failed = true;
            for (int i = 0; i <= 5; i++) {
                if (!br.containsHTML(RECAPTCHATEXT) && !br.containsHTML(RECAPTCHATEXT2)) {
                    logger.warning("Decrypter broken for link: " + parameter);
                    return null;
                }
                final Recaptcha rc = new Recaptcha(br, this);
                rc.parse();
                rc.load();
                final File cf = rc.downloadCaptcha(getLocalCaptchaFile());
                final String c = getCaptchaCode("recaptcha", cf, param);
                final String postData = "recaptcha_challenge_field=" + rc.getChallenge() + "&recaptcha_response_field=" + Encoding.urlEncode(c) + "&x=" + Integer.toString(new Random().nextInt(100)) + "&y=" + Integer.toString(new Random().nextInt(100)) + "&captcha=recaptcha&mon_captcha=" + linkid;
                br.postPage("http://" + domain + "/showlinks.php", postData);
                if (br.containsHTML(">Captcha erron?? vous allez ??tre rediriger")) {
                    /* Prefer reCaptcha */
                    br.getPage(parameter + "&c=1");
                    continue;
                }
                failed = false;
                break;
            }
            if (failed) {
                throw new DecrypterException(DecrypterException.CAPTCHA);
            }
        } else {
            if (rcChallenge == null) {
                logger.warning("Decrypter broken for link: " + parameter);
                return null;
            }
            br.postPage("http://vosprotects.com/showlinks.php", "recaptcha_challenge_field=" + Encoding.urlEncode(rcChallenge) + "&recaptcha_response_field=&linkid=" + linkid + "&x=" + Integer.toString(new Random().nextInt(100)) + "&y=" + Integer.toString(new Random().nextInt(100)));
        }

        br.postPage("http://" + domain + "/linkid.php", "security_code=&password=&linkid=" + linkid + "&x=" + Integer.toString(new Random().nextInt(100)) + "&y=" + Integer.toString(new Random().nextInt(100)));
        final String fpName = br.getRegex("<div align=\"center\">([^<>\"]*?)</div>").getMatch(0);
        String[] links = br.getRegex("href=(https?://[^<>\"']+) target=_blank>").getColumn(0);
        if (links == null || links.length == 0) {
            if (br.containsHTML("href= target=_blank></a><br></br><a")) {
                logger.info("Link offline: " + parameter);
                return decryptedLinks;
            }
            logger.warning("Decrypter broken for link: " + parameter);
            return null;
        }
        for (String singleLink : links) {
            if (!singleLink.contains(domain)) {
                decryptedLinks.add(createDownloadlink(singleLink));
            }
        }
        if (fpName != null) {
            final FilePackage fp = FilePackage.getInstance();
            fp.setName(Encoding.htmlDecode(fpName.trim()));
            fp.addLinks(decryptedLinks);
        }
        return decryptedLinks;
    }

    /* NO OVERRIDE!! */
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }

}