//    jDownloader - Downloadmanager
//    Copyright (C) 2009  JD-Team support@jdownloader.org
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
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterException;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.PluginForDecrypt;
import jd.utils.locale.JDL;

@DecrypterPlugin(revision = "$Revision: 34675 $", interfaceVersion = 2, names = { "urlcut.com" }, urls = { "http://[\\w\\.]*?urlcut\\.com/[0-9a-zA-Z]+" }) 
public class RlCtCm extends PluginForDecrypt {

    public RlCtCm(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        String parameter = param.toString();
        br.setFollowRedirects(false);
        br.getPage(parameter);
        String page = br.getRedirectLocation();
        if (page.contains("/password.html")) {
            for (int i = 0; i < 5; i++) {
                br.getPage(page);
                String linkid = br.getRegex("name=u value=\"(.*?)\"").getMatch(0);
                String passwordstring = getUserInput(null, param);
                br.postPage("http://urlcut.com/password.cgi", "u=" + linkid + "&p=" + Encoding.urlEncode(passwordstring));
                page = null;
                page = br.getRedirectLocation();
                br.getPage(page);
                page = br.getRedirectLocation();
                if (!page.contains("/password.html")) break;
                if (i == 4) throw new DecrypterException(JDL.L("plugins.decrypter.urtcutcom.badpassword", "You have entered bad password 5 times. Please review your data."));
            }
            decryptedLinks.add(createDownloadlink(page));
        } else {
            decryptedLinks.add(createDownloadlink(page));
        }
        return decryptedLinks;
    }

    /* NO OVERRIDE!! */
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }

}