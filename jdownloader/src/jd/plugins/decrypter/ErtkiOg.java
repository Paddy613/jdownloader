//    jDownloader - Downloadmanager
//    Copyright (C) 2012  JD-Team support@jdownloader.org
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with this program.  If not, see <http://www.gnu.org/licenses/>.

package jd.plugins.decrypter;

import java.util.ArrayList;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.PluginForDecrypt;

@DecrypterPlugin(revision = "$Revision: 34675 $", interfaceVersion = 3, names = { "erotelki.org" }, urls = { "http://(www\\.)?erotelki\\.org/([\\w\\-]+/([\\w\\-]+/)?\\d+\\-[\\w+\\-]+\\.html|engine/go\\.php\\?url=[^<>\"\\']+)" }) 
public class ErtkiOg extends PluginForDecrypt {

    public ErtkiOg(PluginWrapper wrapper) {
        super(wrapper);
    }

    // DEV NOTES
    // newer content is base64encoded with occasional htmlencoding characters at
    // the end of string, mainly for =chars

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        String parameter = param.toString();
        br.setFollowRedirects(false);
        br.setCookiesExclusive(true);
        if (parameter.matches("http://(www\\.)?erotelki\\.org/[\\w\\-]+/([\\w\\-]+/)?\\d+\\-[\\w+\\-]+\\.html")) {
            br.getPage(parameter);
            if (br.containsHTML(">?? ??????????????????, ???????????? ???????????????? ?????? ?????? ???? ????????????????, ???????????????? ?????? ?????????????? ???? ?????????? ?????? ?????? ???????? ??????????????\\.") || this.br.getHttpConnection().getResponseCode() == 404) {
                decryptedLinks.add(this.createOfflinelink(parameter));
                return decryptedLinks;
            }
            // set packagename
            String fpName = br.getRegex("<title>(.*?) \\&raquo\\;").getMatch(0);
            if (fpName == null) {
                fpName = br.getRegex("<h1 class=\"title_h\">(.*?)</h1>").getMatch(0);
            }
            if (fpName == null) {
                fpName = br.getRegex("<meta name=\"description\" content=\"NUDolls (.*?)\" />").getMatch(0);
            }
            // now we find
            final String[] regexes = { "url=([^<>\"\\']+)", "<a href=\"([^\"\\'<>]+)\" target=\"_blank\">", "href=\"(http://(www\\.)?erotelki\\.org/uploads/posts/[^<>\"]*?)\" onclick=\"return hs\\.expand", "\"(http://(www\\.)?erotelki\\.org/uploads/posts/[^<>\"]*?)\"" };
            for (final String regex : regexes) {
                final String[] finallinks = br.getRegex(regex).getColumn(0);
                if (finallinks != null) {
                    for (final String link : finallinks) {
                        /* Skip these as we get them directly via RegEx already */
                        if (link.contains("engine/")) {
                            continue;
                        }
                        String final_link;
                        if (!link.startsWith("http")) {
                            final_link = Encoding.Base64Decode(Encoding.htmlDecode(link));
                        } else {
                            final_link = link;
                            if (final_link.matches(".+erotelki\\.org/uploads/.+")) {
                                final_link = "directhttp://" + final_link;
                            }
                        }
                        decryptedLinks.add(createDownloadlink(final_link));
                    }
                }
            }

            if (fpName != null) {
                FilePackage fp = FilePackage.getInstance();
                fp.setName(fpName);
                fp.addLinks(decryptedLinks);
            }
        } else {
            String finallink = Encoding.Base64Decode(Encoding.htmlDecode(new Regex(parameter, "url=([^<>\"\\']+)").getMatch(0)));
            decryptedLinks.add(createDownloadlink(finallink));
        }

        return decryptedLinks;
    }

    /* NO OVERRIDE!! */
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }

}