//jDownloader - Downloadmanager
//Copyright (C) 2013  JD-Team support@jdownloader.org
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import jd.PluginWrapper;
import jd.config.Property;
import jd.http.Browser;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.plugins.Account;
import jd.plugins.AccountInfo;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginConfigPanelNG;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;

import org.jdownloader.captcha.v2.challenge.recaptcha.v1.Recaptcha;
import org.jdownloader.gui.IconKey;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.images.AbstractIcon;
import org.jdownloader.plugins.controller.host.LazyHostPlugin.FEATURE;
import org.jdownloader.translate._JDT;

@HostPlugin(revision = "$Revision: 35284 $", interfaceVersion = 3, names = { "simply-premium.com" }, urls = { "REGEX_NOT_POSSIBLE_RANDOM-asdfasdfsadfsfs2133" }) 
public class SimplyPremiumCom extends PluginForHost {
    private static HashMap<Account, HashMap<String, Long>> hostUnavailableMap = new HashMap<Account, HashMap<String, Long>>();
    private static final String                            NOCHUNKS           = "NOCHUNKS";
    private static final String                            NICE_HOST          = "simply-premium.com";
    private static final String                            NICE_HOSTproperty  = "simplypremiumcom";
    private static String                                  APIKEY             = null;
    private static Object                                  LOCK               = new Object();

    public SimplyPremiumCom(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium("http://www.simply-premium.com/vip.php");
    }

    @Override
    public String getAGBLink() {
        return "http://www.simply-premium.com/terms_and_conditions.php";
    }

    private Browser newBrowser() {
        br = new Browser();
        br.setCookiesExclusive(true);
        /* Admin asked us to make sure we follow redirects. */
        br.setFollowRedirects(true);
        /* define custom browser headers and language settings. */
        br.getHeaders().put("Accept", "application/json");
        br.getHeaders().put("User-Agent", "JDownloader");
        br.setCustomCharset("utf-8");
        br.setConnectTimeout(60 * 1000);
        br.setReadTimeout(60 * 1000);
        return br;
    }

    @Override
    public AvailableStatus requestFileInformation(DownloadLink link) throws PluginException {
        return AvailableStatus.UNCHECKABLE;
    }

    @Override
    public boolean canHandle(DownloadLink downloadLink, Account account) throws Exception {
        if (account == null) {
            /* without account its not possible to download the link */
            return false;
        }
        return true;
    }

    @Override
    public void handleFree(DownloadLink downloadLink) throws Exception, PluginException {
        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return 0;
    }

    @Override
    public void handlePremium(DownloadLink link, Account account) throws Exception {
        /* handle premium should never be called */
        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
    }

    private void handleDL(final Account account, final DownloadLink link, final String dllink) throws Exception {
        final boolean resume_allowed = account.getBooleanProperty("resume_allowed", false);
        int maxChunks = (int) account.getLongProperty("maxconnections", 1);
        if (maxChunks > 20) {
            maxChunks = 0;
        }
        if (link.getBooleanProperty(NOCHUNKS, false)) {
            maxChunks = 1;
        }
        if (!resume_allowed) {
            maxChunks = 1;
        }
        link.setProperty(NICE_HOSTproperty + "directlink", dllink);
        dl = jd.plugins.BrowserAdapter.openDownload(br, link, dllink, resume_allowed, maxChunks);
        if (dl.getConnection().getContentType().contains("html")) {
            br.followConnection();
            downloadErrorhandling(account, link);
            logger.info(NICE_HOST + ": Unknown download error");
            int timesFailed = link.getIntegerProperty(NICE_HOSTproperty + "timesfailed_unknowndlerror", 0);
            link.getLinkStatus().setRetryCount(0);
            if (timesFailed <= 2) {
                timesFailed++;
                link.setProperty(NICE_HOSTproperty + "timesfailed_unknowndlerror", timesFailed);
                throw new PluginException(LinkStatus.ERROR_RETRY, "Unknown error");
            } else {
                link.setProperty(NICE_HOSTproperty + "timesfailed_unknowndlerror", Property.NULL);
                logger.info(NICE_HOST + ": Unknown error - disabling current host!");
                tempUnavailableHoster(account, link, 60 * 60 * 1000l);
            }
        }
        try {
            if (!this.dl.startDownload()) {
                try {
                    if (dl.externalDownloadStop()) {
                        return;
                    }
                } catch (final Throwable e) {
                }
                /* unknown error, we disable multiple chunks */
                if (link.getBooleanProperty(SimplyPremiumCom.NOCHUNKS, false) == false) {
                    link.setProperty(SimplyPremiumCom.NOCHUNKS, Boolean.valueOf(true));
                    throw new PluginException(LinkStatus.ERROR_RETRY);
                }
            }
        } catch (final PluginException e) {
            // New V2 errorhandling
            /* unknown error, we disable multiple chunks */
            if (e.getLinkStatus() != LinkStatus.ERROR_RETRY && link.getBooleanProperty(SimplyPremiumCom.NOCHUNKS, false) == false) {
                link.setProperty(SimplyPremiumCom.NOCHUNKS, Boolean.valueOf(true));
                throw new PluginException(LinkStatus.ERROR_RETRY);
            }
            throw e;
        }
    }

    @Override
    public FEATURE[] getFeatures() {
        return new FEATURE[] { FEATURE.MULTIHOST };
    }

    @SuppressWarnings("deprecation")
    @Override
    public void handleMultiHost(final DownloadLink link, final Account account) throws Exception {
        this.br = newBrowser();
        synchronized (hostUnavailableMap) {
            HashMap<String, Long> unavailableMap = hostUnavailableMap.get(account);
            if (unavailableMap != null) {
                Long lastUnavailable = unavailableMap.get(link.getHost());
                if (lastUnavailable != null && System.currentTimeMillis() < lastUnavailable) {
                    final long wait = lastUnavailable - System.currentTimeMillis();
                    throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Host is temporarily unavailable via " + this.getHost(), wait);
                } else if (lastUnavailable != null) {
                    unavailableMap.remove(link.getHost());
                    if (unavailableMap.size() == 0) {
                        hostUnavailableMap.remove(account);
                    }
                }
            }
        }
        getapikey(account);
        showMessage(link, "Task 1: Checking link");
        String dllink = checkDirectLink(link, NICE_HOSTproperty + "directlink");
        if (dllink == null) {
            /* request download information */
            br.getPage("http://www.simply-premium.com/premium.php?info=&link=" + Encoding.urlEncode(link.getDownloadURL()));
            downloadErrorhandling(account, link);
            /* request download */
            dllink = getXML("download");
            if (dllink == null) {
                logger.info(NICE_HOST + ": dllinknull");
                int timesFailed = link.getIntegerProperty(NICE_HOSTproperty + "timesfailed_dllinknull", 0);
                link.getLinkStatus().setRetryCount(0);
                if (timesFailed <= 2) {
                    timesFailed++;
                    link.setProperty(NICE_HOSTproperty + "timesfailed_dllinknull", timesFailed);
                    throw new PluginException(LinkStatus.ERROR_RETRY, "dllinknull");
                } else {
                    link.setProperty(NICE_HOSTproperty + "timesfailed_dllinknull", Property.NULL);
                    logger.info(NICE_HOST + ": dllinknull - disabling current host!");
                    tempUnavailableHoster(account, link, 60 * 60 * 1000l);
                }
            }
        }
        showMessage(link, "Task 2: Download begins!");
        handleDL(account, link, dllink);
    }

    private void downloadErrorhandling(final Account account, final DownloadLink link) throws PluginException {
        if (br.containsHTML("<error>NOTFOUND</error>")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        } else if (br.containsHTML("<error>hostererror</error>")) {
            logger.info(NICE_HOST + ": Host is unavailable at the moment -> Disabling it");
            tempUnavailableHoster(account, link, 60 * 60 * 1000l);
        } else if (br.containsHTML("<error>maxconnection</error>")) {
            logger.info(NICE_HOST + ": Too many simultan connections");
            throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "Wait before starting new downloads", 5 * 60 * 1000l);
        } else if (br.containsHTML("<error>notvalid</error>")) {
            logger.info(NICE_HOST + ": Account invalid -> Disabling it");
            final String lang = System.getProperty("user.language");
            if ("de".equalsIgnoreCase(lang)) {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUng??ltiger Benutzername oder ung??ltiges Passwort!\r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen? Versuche folgendes:\r\n1. Falls dein Passwort Sonderzeichen enth??lt, ??ndere es (entferne diese) und versuche es erneut!\r\n2. Gib deine Zugangsdaten per Hand (ohne kopieren/einf??gen) ein.", PluginException.VALUE_ID_PREMIUM_DISABLE);
            } else {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password!\r\nYou're sure that the username and password you entered are correct? Some hints:\r\n1. If your password contains special characters, change it (remove them) and try again!\r\n2. Type in your username/password by hand without copy & paste.", PluginException.VALUE_ID_PREMIUM_DISABLE);
            }
        } else if (br.containsHTML("<error>trafficlimit</error>")) {
            logger.info(NICE_HOST + ": Traffic limit reached -> Temp. disabling account");
            throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_TEMP_DISABLE);
        }
    }

    private String checkDirectLink(final DownloadLink downloadLink, final String property) {
        String dllink = downloadLink.getStringProperty(property);
        if (dllink != null) {
            try {
                final Browser br2 = br.cloneBrowser();
                URLConnectionAdapter con = br2.openHeadConnection(dllink);
                if (con.getContentType().contains("html") || con.getLongContentLength() == -1) {
                    downloadLink.setProperty(property, Property.NULL);
                    dllink = null;
                }
                con.disconnect();
            } catch (final Exception e) {
                downloadLink.setProperty(property, Property.NULL);
                dllink = null;
            }
        }
        return dllink;
    }

    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        this.br = newBrowser();
        final AccountInfo ai = new AccountInfo();
        getapikey(account);
        br.getPage("http://simply-premium.com/api/user.php?apikey=" + APIKEY);
        final String acctype = getXML("account_typ");
        if (acctype == null) {
            final String lang = System.getProperty("user.language");
            if ("de".equalsIgnoreCase(lang)) {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUng??ltiger Benutzername oder ung??ltiges Passwort!\r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen? Versuche folgendes:\r\n1. Falls dein Passwort Sonderzeichen enth??lt, ??ndere es (entferne diese) und versuche es erneut!\r\n2. Gib deine Zugangsdaten per Hand (ohne kopieren/einf??gen) ein.", PluginException.VALUE_ID_PREMIUM_DISABLE);
            } else {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password!\r\nYou're sure that the username and password you entered are correct? Some hints:\r\n1. If your password contains special characters, change it (remove them) and try again!\r\n2. Type in your username/password by hand without copy & paste.", PluginException.VALUE_ID_PREMIUM_DISABLE);
            }
        }
        if (!acctype.matches("1|2") || !br.containsHTML("<vip>1</vip>")) {
            final String lang = System.getProperty("user.language");
            if ("de".equalsIgnoreCase(lang)) {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nNicht unterst??tzter Accounttyp!", PluginException.VALUE_ID_PREMIUM_DISABLE);
            } else {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUnsupported account type!", PluginException.VALUE_ID_PREMIUM_DISABLE);
            }
        }
        final String trafficleft = getXML("remain_traffic");
        ai.setTrafficLeft(trafficleft);
        String accdesc = null;
        if ("1".equals(acctype)) {
            String expire = getXML("timeend");
            expire = expire.trim();
            if (expire.equals("")) {
                ai.setExpired(true);
                return ai;
            }
            final Long expirelng = Long.parseLong(expire);
            ai.setValidUntil(expirelng * 1000);
            final String max_traffic = getXML("max_traffic");
            /* max_traffic is not always given */
            if (max_traffic != null) {
                ai.setTrafficMax(Long.parseLong(max_traffic));
            }
            accdesc = _JDT.T.plugins_simplypremiumcom_ACCOUNT_TYPE_TIME();
        } else {
            accdesc = _JDT.T.plugins_simplypremiumcom_ACCOUNT_TYPE_VOLUME();
        }
        int maxSimultanDls = Integer.parseInt(getXML("max_downloads"));
        if (maxSimultanDls < 1) {
            maxSimultanDls = 1;
        } else if (maxSimultanDls > 20) {
            maxSimultanDls = -1;
        }
        long maxChunks = Integer.parseInt(getXML("chunks"));
        if (maxChunks > 20) {
            maxChunks = 0;
        } else if (maxChunks > 1) {
            maxChunks = -maxChunks;
        }
        final boolean resumeAllowed = "1".equals(getXML("resume"));
        account.setProperty("maxconnections", maxChunks);
        account.setProperty("max_downloads", maxSimultanDls);
        account.setProperty("acc_type", accdesc);
        account.setProperty("resume_allowed", resumeAllowed);
        /* online=1 == show only working hosts */
        br.getPage("http://www.simply-premium.com/api/hosts.php?online=1");
        final String[] hostDomains = br.getRegex("<host>([^<>\"]*?)</host>").getColumn(0);
        if (hostDomains != null) {
            final ArrayList<String> supportedHosts = new ArrayList<String>(Arrays.asList(hostDomains));
            ai.setMultiHostSupport(this, supportedHosts);
        }
        account.setMaxSimultanDownloads(maxSimultanDls);
        account.setConcurrentUsePossible(true);
        account.setValid(true);
        ai.setStatus(accdesc);
        return ai;
    }

    private void getapikey(final Account acc) throws IOException, Exception {
        synchronized (LOCK) {
            boolean acmatch = Encoding.urlEncode(acc.getUser()).equals(acc.getStringProperty("name", Encoding.urlEncode(acc.getUser())));
            if (acmatch) {
                acmatch = Encoding.urlEncode(acc.getPass()).equals(acc.getStringProperty("pass", Encoding.urlEncode(acc.getPass())));
            }
            APIKEY = acc.getStringProperty(NICE_HOSTproperty + "apikey", null);
            if (APIKEY != null && acmatch) {
                br.setCookie("http://simply-premium.com/", "apikey", APIKEY);
            } else {
                login(acc);
            }
        }
    }

    private void login(final Account account) throws IOException, Exception {
        final String lang = System.getProperty("user.language");
        br.getPage("http://simply-premium.com/login.php?login_name=" + Encoding.urlEncode(account.getUser()) + "&login_pass=" + Encoding.urlEncode(account.getPass()));
        if (br.containsHTML("<error>captcha_required</error>")) {
            final DownloadLink dummyLink = new DownloadLink(this, "Account", "simply-premium.com", "http://simply-premium.com", true);
            final Recaptcha rc = new Recaptcha(br, this);
            final String rcID = getXML("captcha");
            rc.setId(rcID);
            rc.load();
            for (int i = 1; i <= 3; i++) {
                final File cf = rc.downloadCaptcha(getLocalCaptchaFile());
                final String c = getCaptchaCode("recaptcha", cf, dummyLink);
                br.getPage("http://simply-premium.com/login.php?login_name=" + Encoding.urlEncode(account.getUser()) + "&login_pass=" + Encoding.urlEncode(account.getPass()) + "&recaptcha_challenge_field=" + rc.getChallenge() + "&recaptcha_response_field=" + Encoding.urlEncode(c));
                if (br.containsHTML("<error>captcha_incorrect</error>")) {
                    rc.reload();
                    continue;
                }
                break;
            }
            if (br.containsHTML("<error>captcha_incorrect</error>")) {
                if ("de".equalsIgnoreCase(lang)) {
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUng??ltiger Benutzername, ung??ltiges Passwort und/oder ung??ltiges Login-Captcha!\r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen? Versuche folgendes:\r\n1. Falls dein Passwort Sonderzeichen enth??lt, ??ndere es (entferne diese) und versuche es erneut!\r\n2. Gib deine Zugangsdaten per Hand (ohne kopieren/einf??gen) ein.", PluginException.VALUE_ID_PREMIUM_DISABLE);
                } else {
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password and/or invalid login-captcha!\r\nYou're sure that the username and password you entered are correct? Some hints:\r\n1. If your password contains special characters, change it (remove them) and try again!\r\n2. Type in your username/password by hand without copy & paste.", PluginException.VALUE_ID_PREMIUM_DISABLE);
                }
            }
        }
        if (br.containsHTML("<error>not_valid_ip</error>")) {
            throw new PluginException(LinkStatus.ERROR_PREMIUM, "Invalid IP / Ung??ltige IP", PluginException.VALUE_ID_PREMIUM_DISABLE);
        } else if (br.containsHTML("<error>no_traffic</error>")) {
            logger.info("Account has no traffic");
            if ("de".equalsIgnoreCase(lang)) {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "Kein Traffic", PluginException.VALUE_ID_PREMIUM_TEMP_DISABLE);
            } else {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "No traffic", PluginException.VALUE_ID_PREMIUM_TEMP_DISABLE);
            }
        } else if (br.containsHTML("<error>no_longer_valid</error>")) {
            account.getAccountInfo().setExpired(true);
            account.setValid(false);
            if ("de".equalsIgnoreCase(lang)) {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nAccount abgelaufen!", PluginException.VALUE_ID_PREMIUM_TEMP_DISABLE);
            } else {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nAccount expired!", PluginException.VALUE_ID_PREMIUM_TEMP_DISABLE);
            }
        }
        APIKEY = br.getRegex("<apikey>([A-Za-z0-9]+)</apikey>").getMatch(0);
        if (APIKEY == null || br.containsHTML("<error>not_valid</error>")) {
            if ("de".equalsIgnoreCase(lang)) {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUng??ltiger Benutzername oder ung??ltiges Passwort!\r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen? Versuche folgendes:\r\n1. Falls dein Passwort Sonderzeichen enth??lt, ??ndere es (entferne diese) und versuche es erneut!\r\n2. Gib deine Zugangsdaten per Hand (ohne kopieren/einf??gen) ein.", PluginException.VALUE_ID_PREMIUM_DISABLE);
            } else {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password!\r\nYou're sure that the username and password you entered are correct? Some hints:\r\n1. If your password contains special characters, change it (remove them) and try again!\r\n2. Type in your username/password by hand without copy & paste.", PluginException.VALUE_ID_PREMIUM_DISABLE);
            }
        }
        account.setProperty(NICE_HOSTproperty + "apikey", APIKEY);
        account.setProperty("name", Encoding.urlEncode(account.getUser()));
        account.setProperty("pass", Encoding.urlEncode(account.getPass()));
    }

    private void showMessage(DownloadLink link, String message) {
        link.getLinkStatus().setStatusText(message);
    }

    private void tempUnavailableHoster(final Account account, final DownloadLink downloadLink, final long timeout) throws PluginException {
        if (downloadLink == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT, "Unable to handle this errorcode!");
        }
        synchronized (hostUnavailableMap) {
            HashMap<String, Long> unavailableMap = hostUnavailableMap.get(account);
            if (unavailableMap == null) {
                unavailableMap = new HashMap<String, Long>();
                hostUnavailableMap.put(account, unavailableMap);
            }
            /* wait 30 mins to retry this host */
            unavailableMap.put(downloadLink.getHost(), (System.currentTimeMillis() + timeout));
        }
        throw new PluginException(LinkStatus.ERROR_RETRY);
    }

    private String getXML(final String parameter) {
        return br.getRegex("<" + parameter + "( type=\"[^<>\"/]*?\")?>([^<>]*?)</" + parameter + ">").getMatch(1);
    }

    @Override
    public void extendAccountSettingsPanel(Account account, PluginConfigPanelNG panel) {
        final AccountInfo ai = account.getAccountInfo();
        if (ai != null) {
            int maxChunks = (int) account.getLongProperty("maxconnections", 1);
            if (maxChunks < 0) {
                maxChunks = maxChunks * -1;
            }
            int max_dls = (int) account.getLongProperty("max_downloads", 1);
            /* User should see an understandable number. */
            if (max_dls == -1) {
                max_dls = 20;
            }
            final boolean resume_allowed = account.getBooleanProperty("resume_allowed", false);
            String resume_string;
            if (resume_allowed) {
                resume_string = _JDT.T.literally_yes();
            } else {
                resume_string = _JDT.T.lit_no();
            }
            panel.addHeader(_GUI.T.lit_download(), new AbstractIcon(IconKey.ICON_DOWNLOAD, 18));
            panel.addStringPair(_GUI.T.lit_max_simultanous_downloads(), Integer.toString(max_dls));
            panel.addStringPair(_GUI.T.lit_max_chunks_per_link(), Integer.toString(maxChunks));
            panel.addStringPair(_GUI.T.lit_interrupted_downloads_are_resumable(), resume_string);
        }
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }

    /* NO OVERRIDE!! We need to stay 0.9*compatible */
    public boolean hasCaptcha(DownloadLink link, jd.plugins.Account acc) {
        if (acc == null) {
            /* no account, yes we can expect captcha */
            return true;
        }
        if (Boolean.TRUE.equals(acc.getBooleanProperty("free"))) {
            /* free accounts also have captchas */
            return true;
        }
        if (Boolean.TRUE.equals(acc.getBooleanProperty("nopremium"))) {
            /* free accounts also have captchas */
            return true;
        }
        if (acc.getStringProperty("session_type") != null && !"premium".equalsIgnoreCase(acc.getStringProperty("session_type"))) {
            return true;
        }
        return false;
    }
}