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

import java.util.ArrayList;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterException;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.Plugin;
import jd.plugins.PluginForDecrypt;

@DecrypterPlugin(revision = "$Revision: 34675 $", interfaceVersion = 2, names = { "data.hu" }, urls = { "http://[\\w\\.]*?data\\.hu/dir/[0-9a-z]+" }) 
public class DataHuFolder extends PluginForDecrypt {

    public DataHuFolder(PluginWrapper wrapper) {
        super(wrapper);
    }

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        String parameter = param.toString();
        br.setCustomCharset("utf-8");
        br.setFollowRedirects(true);
        br.setCookie("http://data.hu", "lang", "en");
        br.getPage(parameter);
        if (br.containsHTML("class=\"error\"")) {
            logger.info("Link offline: " + parameter);
            return decryptedLinks;
        }
        /** Password protected folders */
        if (br.containsHTML("K??rlek add meg a jelsz??t\\!<")) {
            for (int i = 0; i <= 3; i++) {
                final String passCode = Plugin.getUserInput("Enter password for: " + parameter, param);
                br.postPage(parameter, "mappa_pass=" + Encoding.urlEncode(passCode));
                if (br.containsHTML(">Hib??s jelsz??")) continue;
                break;
            }
            if (br.containsHTML(">Hib??s jelsz??")) throw new DecrypterException(DecrypterException.PASSWORD);
        }
        String[] links = br.getRegex("\\'(http://[\\w\\.]*?data\\.hu/get/\\d+/.*?)\\'").getColumn(0);
        String[] folders = br.getRegex("\\'(http://[\\w\\.]*?data\\.hu/dir/[0-9a-z]+)\\'").getColumn(0);
        if ((links == null || links.length == 0) && (folders == null || folders.length == 0)) {
            logger.warning("Decrypter broken for link: " + parameter);
            return null;
        }
        if (links != null && links.length != 0) {
            for (String dl : links)
                decryptedLinks.add(createDownloadlink(dl));
        }
        final String currentFolderID = new Regex(parameter, "data\\.hu/(dir/.+)").getMatch(0);
        if (folders != null && folders.length != 0) {
            for (String folderlink : folders)
                if (!folderlink.contains(currentFolderID)) decryptedLinks.add(createDownloadlink(folderlink));
        }
        return decryptedLinks;
    }

    /* NO OVERRIDE!! */
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }

}