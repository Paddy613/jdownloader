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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jd.PluginWrapper;
import jd.config.ConfigContainer;
import jd.config.ConfigEntry;
import jd.config.Property;
import jd.controlling.AccountController;
import jd.http.Browser;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.parser.html.Form;
import jd.plugins.Account;
import jd.plugins.Account.AccountType;
import jd.plugins.AccountInfo;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.components.PluginJSonUtils;
import jd.utils.locale.JDL;

import org.appwork.utils.StringUtils;
import org.appwork.utils.formatter.SizeFormatter;
import org.appwork.utils.formatter.TimeFormatter;
import org.jdownloader.captcha.v2.challenge.recaptcha.v2.CaptchaHelperHostPluginRecaptchaV2;
import org.jdownloader.plugins.components.antiDDoSForHost;
import org.jdownloader.plugins.controller.host.LazyHostPlugin.FEATURE;

/**
 * Note: prem.link redirects to grab8
 *
 * @author raztoki
 *
 */
@HostPlugin(revision = "$Revision: 35284 $", interfaceVersion = 3, names = { "grab8.com", "prem.link" }, urls = { "https?://(?:\\w+\\.)?grab8.com/dl\\.php\\?id=(\\d+)", "https?://(?:\\w+\\.)?prem.link/dl\\.php\\?id=(\\d+)" })
public class Grab8Com extends antiDDoSForHost {

    private final String                                   NICE_HOSTproperty              = getHost().replaceAll("[-\\.]", "");
    private final String                                   NOCHUNKS                       = NICE_HOSTproperty + "_NOCHUNKS";
    private final String                                   NORESUME                       = NICE_HOSTproperty + "_NORESUME";
    private static final String                            CLEAR_DOWNLOAD_HISTORY         = "CLEAR_DOWNLOAD_HISTORY";
    private final boolean                                  default_clear_download_history = false;

    /* Connection limits */
    private static final boolean                           ACCOUNT_PREMIUM_RESUME         = true;
    private static final int                               ACCOUNT_PREMIUM_MAXCHUNKS      = 0;
    private static final int                               ACCOUNT_PREMIUM_MAXDOWNLOADS   = 20;

    private static HashMap<Account, HashMap<String, Long>> hostUnavailableMap             = new HashMap<Account, HashMap<String, Long>>();
    private Account                                        currAcc                        = null;
    private DownloadLink                                   currDownloadLink               = null;
    private static Object                                  LOCK                           = new Object();
    private Browser                                        ajax                           = null;

    public Grab8Com(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium("https://" + getHost() + "/");
        setConfigElements();
    }

    @Override
    public String getAGBLink() {
        return "https://" + getHost() + "/";
    }

    @Override
    protected boolean useRUA() {
        return true;
    }

    @Override
    protected Browser prepBrowser(final Browser prepBr, final String host) {
        if (!(browserPrepped.containsKey(prepBr) && browserPrepped.get(prepBr) == Boolean.TRUE)) {
            super.prepBrowser(prepBr, host);
            prepBr.setConnectTimeout(180 * 1000);
            prepBr.setReadTimeout(180 * 1000);
        }
        return prepBr;
    }

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws Exception {
        Account account = null;
        {
            final List<Account> accounts = AccountController.getInstance().getValidAccounts(this.getHost());
            if (accounts != null && accounts.size() != 0) {
                // lets sort, premium > non premium
                Collections.sort(accounts, new Comparator<Account>() {
                    @Override
                    public int compare(Account o1, Account o2) {
                        final int io1 = AccountType.PREMIUM.equals(o1.getType()) ? 1 : 0;
                        final int io2 = AccountType.PREMIUM.equals(o2.getType()) ? 1 : 0;
                        return io1 >= io2 ? io1 : io2;
                    }
                });
                final Iterator<Account> it = accounts.iterator();
                while (it.hasNext()) {
                    account = it.next();
                    break;
                }
            }
            if (account == null) {
                logger.info("No account present!");
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
        }
        this.br = new Browser();
        setConstants(account, link);
        login(true, false);
        br.setFollowRedirects(true);
        URLConnectionAdapter con = null;
        try {
            con = openAntiDDoSRequestConnection(br, br.createHeadRequest(link.getDownloadURL()));
            if (con.isContentDisposition() && con.isOK()) {
                link.setName(getFileNameFromHeader(con));
                link.setVerifiedFileSize(con.getLongContentLength());
                return AvailableStatus.TRUE;
            } else {
                if (!link.isNameSet()) {
                    link.setName(new Regex(link, this.getSupportedLinks()).getMatch(0));
                }
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
        } catch (final Exception e) {
            if (e instanceof PluginException) {
                throw e;
            }
            return AvailableStatus.UNCHECKABLE;
        } finally {
            try {
                /* make sure we close connection */
                con.disconnect();
            } catch (final Throwable e) {
            }
        }
    }

    private void setConstants(final Account acc, final DownloadLink dl) {
        this.currAcc = acc;
        this.currDownloadLink = dl;
    }

    @Override
    public boolean canHandle(DownloadLink downloadLink, Account account) throws Exception {
        if (account == null) {
            return false;
        }
        return true;
    }

    @Override
    public void handleFree(DownloadLink downloadLink) throws Exception, PluginException {
        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
    }

    @Override
    public void handlePremium(DownloadLink link, Account account) throws Exception {
        // no need to linkcheck before download!
        setConstants(account, link);
        login(true, false);
        handleDL(account, link, link.getDownloadURL());
    }

    @Override
    public FEATURE[] getFeatures() {
        return new FEATURE[] { FEATURE.MULTIHOST };
    }

    @SuppressWarnings("deprecation")
    @Override
    public void handleMultiHost(final DownloadLink link, final Account account) throws Exception {

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
        br = new Browser();
        setConstants(account, link);
        login(true, false);
        String dllink = checkDirectLink(link, NICE_HOSTproperty + "directlink");
        if (dllink == null) {
            getPage("https://" + getHost() + "/");
            postAPISafe("/ajax/action.php", "action=getlink&link=" + Encoding.urlEncode(link.getDownloadURL()));
            dllink = PluginJSonUtils.getJsonValue(ajax, "linkdown");
            final boolean transload = StringUtils.containsIgnoreCase(PluginJSonUtils.getJsonValue(ajax, "message"), "Transloading in progress") || PluginJSonUtils.parseBoolean(PluginJSonUtils.getJsonValue(ajax, "use_transload"));
            if (transload || StringUtils.isEmpty(dllink)) {
                if (transload) {
                    // TODO: transload/api error handling.
                    throw new PluginException(LinkStatus.ERROR_FATAL, "Unsupported Feature");
                } else if ("captcha".equalsIgnoreCase(PluginJSonUtils.getJsonValue(ajax, "status"))) {
                    // {"status":"captcha","message":"","cid":17021,"src":"http:\/\/p7.grab8.com\/new\/images\/depfile.com_captcha.png?rand=4137","server":"http:\/\/p7.grab8.com\/new\/","link":"https:\/\/depfile.com\/uid","runtime":3.5580089092255}
                    // the multihoster is trying to pass the captcha back to this user.... we don't want a bar of that
                    handleErrorRetries("captcha", 10, 5 * 60 * 1000l);
                } else {
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                // final String transloadUrl = PluginJSonUtils.getJson(ajax, "tstatus_url");
                // getPage(transloadUrl);
                // final Form transloadform = br.getFormbyKey("saveto");
                // if (transloadform != null) {
                // logger.info("Found transloadform --> Submitting it");
                // // submitFormAPISafe(transloadform);
                // } else {
                // logger.warning("Could not find transloadform --> Possible failure");
                // }
                // /*
                // * If e.g. the user already transfered the file to the server but this code tries to do it again for whatever reason we
                // will
                // * not see the transferID in the html although it exists and hasn't changed. In this case we should still have it saved
                // from
                // * the first download attempt.
                // */
                // final String newtransferID = br.getRegex("name=\"files\\[\\]\" value=\"(\\d+)\"").getMatch(0);
                // if (newtransferID != null) {
                // logger.info("Successfully found updated transferID");
                // link.setProperty("transferID", newtransferID);
                // }
                // /* Normal case: Requested file is downloaded to the multihost and downloadable via the multihost. */
                // dllink = br.getRegex("File <b><a href=\"/\\d+/(files/[^<>\"]*?)\"").getMatch(0);
                // /*
                // * If we try an already existing link many times we'll get a site asking us if the download is broken thus it looks
                // * different so we need another RegEx.
                // */
                // if (dllink == null) {
                // dllink = br.getRegex("<b>Download: <a href=\"/\\d+/(files/[^<>\"]*?)\"").getMatch(0);
                // }
                // if (dllink == null) {
                // /* Should never happen */
                // handleErrorRetries("dllinknull", 10, 2 * 60 * 1000l);
                // }
                // // /* Happens sometimes - in the tests it frequently happened with share-online.biz links */
                // // if (dllink.equals("files/ip")) {
                // // handleErrorRetries("dllink_invalid_ip", 10, 2 * 60 * 1000l);
                // // }
            }
        }
        handleDL(account, link, dllink);
    }

    @SuppressWarnings("deprecation")
    private void handleDL(final Account account, final DownloadLink link, final String dllink) throws Exception {
        final boolean deleteAfterDownload = this.getPluginConfig().getBooleanProperty(CLEAR_DOWNLOAD_HISTORY, false);
        /* First set hardcoded limit */
        int maxChunks = ACCOUNT_PREMIUM_MAXCHUNKS;
        /* Then check if chunks failed before. */
        if (link.getBooleanProperty(NICE_HOSTproperty + NOCHUNKS, false)) {
            maxChunks = 1;
        }
        boolean resume = ACCOUNT_PREMIUM_RESUME;
        if (link.getBooleanProperty(NORESUME, false)) {
            resume = false;
            link.setProperty(NORESUME, Boolean.valueOf(false));
        }
        link.setProperty(NICE_HOSTproperty + "directlink", dllink);
        try {
            dl = jd.plugins.BrowserAdapter.openDownload(br, link, dllink, resume, maxChunks);
            if (dl.getConnection().getResponseCode() == 416) {
                logger.info("Resume impossible, disabling it for the next try");
                link.setChunksProgress(null);
                link.setProperty(NORESUME, Boolean.valueOf(true));
                throw new PluginException(LinkStatus.ERROR_RETRY);
            }
            final String contenttype = dl.getConnection().getContentType();
            if (contenttype.contains("html")) {
                if (new Regex(link.getDownloadURL(), this.getSupportedLinks()).matches()) {
                    if (br.getURL().endsWith("/404")) {
                        throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                    }
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                br.followConnection();
                handleErrors(br);
                handleErrorRetries("unknowndlerror", 50, 2 * 60 * 1000l);
            }
            try {
                if (this.dl.startDownload()) {
                    if (deleteAfterDownload) {
                        deleteFileFromServer(dllink);
                    }
                } else {
                    try {
                        if (dl.externalDownloadStop()) {
                            return;
                        }
                    } catch (final Throwable e) {
                    }
                    /* unknown error, we disable multiple chunks */
                    if (link.getBooleanProperty(NICE_HOSTproperty + NOCHUNKS, false) == false) {
                        link.setProperty(NICE_HOSTproperty + NOCHUNKS, Boolean.valueOf(true));
                        throw new PluginException(LinkStatus.ERROR_RETRY);
                    }
                }
            } catch (final PluginException e) {
                /*
                 * Incomplete means that they have a broken or 0byte file on their servers which we cannot correct via plugin - links for
                 * which this happens cannot be downloaded via this multihost, at least for some hours.
                 */
                if (e.getLinkStatus() == LinkStatus.ERROR_DOWNLOAD_INCOMPLETE) {
                    logger.info("ERROR_DOWNLOAD_INCOMPLETE --> Next download candidate");
                    throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Broken file serverside");
                }
                e.printStackTrace();
                // New V2 chunk errorhandling
                /* unknown error, we disable multiple chunks */
                if (link.getBooleanProperty(NICE_HOSTproperty + NOCHUNKS, false) == false) {
                    link.setProperty(NICE_HOSTproperty + NOCHUNKS, Boolean.valueOf(true));
                    throw new PluginException(LinkStatus.ERROR_RETRY);
                }
                throw e;
            }
        } catch (final Exception e) {
            link.setProperty(NICE_HOSTproperty + "directlink", Property.NULL);
            throw e;
        }
    }

    private boolean deleteFileFromServer(final String dllink) {
        try {
            final Browser del = new Browser();
            loadCookies(del);
            getPage(del, "https://" + getHost() + "/account");
            String md5 = null;
            // find the md5checksum
            final String[] results = br.getRegex("<a id=\"link-[a-f0-9]{32}.*?></a>").getColumn(-1);
            if (results != null) {
                for (final String result : results) {
                    if (result.contains(dllink)) {
                        md5 = new Regex(result, "link-([a-f0-9]{32})").getMatch(0);
                        break;
                    }
                }
            }
            if (md5 != null) {
                postAPISafe(del, "/ajax/action.php", "action=delete-files&sel_files%5B%5D=" + md5);
                if ("File deleted!".equals(PluginJSonUtils.getJsonValue(ajax, "message"))) {
                    logger.info("Successfully deleted file from server");
                    return true;
                }
            }
        } catch (final Throwable e) {
        }
        logger.warning("Failed to delete file from server");
        return false;
    }

    private String checkDirectLink(final DownloadLink downloadLink, final String property) {
        String dllink = downloadLink.getStringProperty(property);
        if (dllink != null) {
            URLConnectionAdapter con = null;
            try {
                final Browser br2 = br.cloneBrowser();
                con = br2.openHeadConnection(dllink);
                if (con.getContentType().contains("html") || con.getLongContentLength() == -1) {
                    downloadLink.setProperty(property, Property.NULL);
                    dllink = null;
                }
            } catch (final Exception e) {
                downloadLink.setProperty(property, Property.NULL);
                dllink = null;
            } finally {
                try {
                    con.disconnect();
                } catch (final Throwable e) {
                }
            }
        }
        return dllink;
    }

    @SuppressWarnings({ "deprecation" })
    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        setConstants(account, null);
        return login(true, true);
    }

    private Long getExpire() {
        String expire = br.getRegex("<p><b>Expiry</b>:&nbsp;(\\d{2}-\\d{2}-\\d{4})</p>").getMatch(0);
        Long time = TimeFormatter.getMilliSeconds(expire, "MM-dd-yyyy", Locale.ENGLISH);
        if (time != null && time != -1) {
            return time;
        }
        expire = br.getRegex("<p><b>Expiry</b>:&nbsp;(\\w+ \\w+, \\d{4})</p>").getMatch(0);
        if (expire != null) {
            expire = expire.replaceAll("(\\d+)(st|nd|rd|th) ", "$1 ");
        }
        time = TimeFormatter.getMilliSeconds(expire, "dd MMM, yyyy", Locale.ENGLISH);
        return time;
    }

    /**
     * IMPORTANT: If a users' account gets banned, their servers will return the exact same message as if the user entered invalid login
     * data - there is no way to differ between these two states!
     *
     * @throws Exception
     */
    private AccountInfo login(final boolean cachedLogin, final boolean fetchAccountInfo) throws Exception {
        synchronized (LOCK) {
            AccountInfo ai = new AccountInfo();
            try {
                // new browser
                Browser superbrbefore = this.br;
                final Browser br = new Browser();
                if (!fetchAccountInfo && cachedLogin && loadCookies(this.br)) {
                    return currAcc.getAccountInfo();
                } else if (cachedLogin && loadCookies(br)) {
                    // empty
                } else {
                    getPage(br, "https://" + getHost() + "/");
                    // find the form
                    final Form login = br.getFormByInputFieldKeyValue("username", null);
                    if (login == null) {
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                    // https request, login action seems to be to http.
                    login.put("username", Encoding.urlEncode(currAcc.getUser()));
                    login.put("password", Encoding.urlEncode(currAcc.getPass()));
                    login.put("rememberme", "true");
                    // on https request, images are http (hard coded).
                    final String url_ordinary_captcha = login.getRegex("(?:https?:)?(?://(?:www\\.)?[^/]+)?/captcha\\.php\\?tp=login[^<>\"']+").getMatch(-1);
                    if (login.containsHTML("name=\"g-recaptcha-response\"")) {
                        final DownloadLink dummyLink = new DownloadLink(this, "Account Login", getHost(), getHost(), true);
                        final DownloadLink odl = this.getDownloadLink();
                        this.setDownloadLink(dummyLink);
                        final String recaptchaV2Response = new CaptchaHelperHostPluginRecaptchaV2(this, br).getToken();
                        login.put("g-recaptcha-response", Encoding.urlEncode(recaptchaV2Response));
                        if (odl != null) {
                            this.setDownloadLink(odl);
                        }
                    } else if (url_ordinary_captcha != null) {
                        final DownloadLink dummyLink = new DownloadLink(this, "Account Login", getHost(), getHost(), true);
                        final DownloadLink odl = this.getDownloadLink();
                        this.setDownloadLink(dummyLink);
                        this.br = br;
                        final String code = this.getCaptchaCode(url_ordinary_captcha, dummyLink);
                        this.br = superbrbefore;
                        login.put("icaptcha", Encoding.urlEncode(code));
                        if (odl != null) {
                            this.setDownloadLink(odl);
                        }
                    }
                    if (login.getAction() == null) {
                        login.setAction("/ajax/action.php");
                    }
                    submitFormAPISafe(br, login);
                    if (inValidateCookies(br, new String[] { "auth", "user" })) {
                        if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                            throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUng??ltiger Benutzername,/Passwort!\r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen? Versuche folgendes:\r\n1. Falls dein Passwort Sonderzeichen enth??lt, ??ndere es (entferne diese) und versuche es erneut!\r\n2. Gib deine Zugangsdaten per Hand (ohne kopieren/einf??gen) ein.", PluginException.VALUE_ID_PREMIUM_DISABLE);
                        } else {
                            throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password!\r\nYou're sure that the username and password you entered are correct? Some hints:\r\n1. If your password contains special characters, change it (remove them) and try again!\r\n2. Type in your username/password by hand without copy & paste.", PluginException.VALUE_ID_PREMIUM_DISABLE);
                        }
                    }
                }
                // following ensures account is premium still, and expire time/hosts are set.
                br.setFollowRedirects(true);
                getPage(br, "https://" + getHost() + "/account");
                if (br.containsHTML("You are currently a FREE User")) {
                    // this code is when user adds prem.link account to grab8 it will give you this error message. It's not that free
                    // accounts are not supported!
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, "You are currently a FREE User!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                }
                // available traffic
                final String[] traffic = br.getRegex("<p><b>Traffic</b>:&nbsp;([0-9\\.]+ (?:[KMG]{0,1}B)?)\\s*/\\s*([0-9\\.]+ GB)</p>").getRow(0);
                if (traffic == null || traffic.length != 2) {
                    if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nPlugin defekt, bitte den JDownloader Support kontaktieren!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else if ("pl".equalsIgnoreCase(System.getProperty("user.language"))) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nB????d wtyczki, skontaktuj si?? z Supportem JDownloadera!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nPlugin broken, please contact the JDownloader Support!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                }
                // they show traffic used not left.
                ai.setTrafficLeft(SizeFormatter.getSize(traffic[1]) - SizeFormatter.getSize(traffic[0]));
                ai.setTrafficMax(SizeFormatter.getSize(traffic[1]));
                // is account free account?
                boolean freeAccount = isAccountFree(br);
                final Long expire = getExpire();
                if (!freeAccount && expire != null && ai.setValidUntil(expire, br) && !ai.isExpired()) {
                    currAcc.setType(AccountType.PREMIUM);
                    ai.setStatus("Premium Account");
                } else {
                    currAcc.setType(AccountType.FREE);
                    ai.setStatus("Free Account");
                }
                currAcc.setValid(true);
                // get hostmap from /hosts, this shows if host is available to free mode and if its up and down...
                getPage(br, "/hosts");
                final ArrayList<String> supportedHosts = new ArrayList<String>();
                final String[] tableRow = br.getRegex("<tr>\\s*<td>.*?</tr>").getColumn(-1);
                freeAccount = currAcc.getType() == AccountType.FREE;
                for (final String row : tableRow) {
                    // we should be left with two cleanuped up lines
                    final String cleanup = row.replaceAll("[ ]*<[^>]+>[ ]*", "").replaceAll("[\r\n\t]+", "\r\n").trim();
                    String host = cleanup.split("\r\n")[0];
                    final String online = cleanup.split("\r\n")[1];
                    final boolean free = new Regex(row, ".*/themes/images/free\\.gif").matches();
                    if (host == null || online == null) {
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                    if (StringUtils.endsWithCaseInsensitive(online, "offline") || StringUtils.endsWithCaseInsensitive(online, "fixing")) {
                        continue;
                    }
                    if (freeAccount && !free) {
                        continue;
                    }
                    supportedHosts.add(host);
                }
                // note: on the account page, they do have array of hosts but it only shows ones with traffic limits.
                ai.setMultiHostSupport(this, supportedHosts);

                currAcc.setProperty("name", Encoding.urlEncode(currAcc.getUser()));
                currAcc.setProperty("pass", Encoding.urlEncode(currAcc.getPass()));
                currAcc.setProperty("cookies", fetchCookies(br, getHost()));
                // load cookies to this.br
                loadCookies(this.br);
                return ai;
            } catch (final PluginException e) {
                currAcc.setProperty("cookies", Property.NULL);
                throw e;
            }
        }
    }

    private final boolean isAccountFree(final Browser br) {
        // prem.link seems to be free only service?
        // following is from /account '<p><b>Account type</b>:&nbsp;Register</p>'
        // but / has 'You are currently a <font color="#FF4C4C"><b>FREE</b> </font>member<br>'
        if (br.containsHTML(">Account type</b>:(?:\\s*|&nbsp;)?(Register|Free|Free Account)</p>")) {
            return true;
        }
        return false;
    }

    /**
     * all cookies must match!
     *
     * @author raztoki
     * @param br
     * @param strings
     * @return
     */
    private boolean inValidateCookies(final Browser br, final String... strings) {
        if (strings == null) {
            return true;
        }
        boolean result = false;
        for (final String string : strings) {
            final String cookie = br.getCookie(getHost(), string);
            result = cookie == null || "NULL".equals(cookie) ? true : false;
            if (result) {
                return true;
            }
        }
        return result;
    }

    private boolean loadCookies(final Browser br) {
        synchronized (LOCK) {
            // Load cookies
            br.setCookiesExclusive(true);
            final Object ret = currAcc.getProperty("cookies", null);
            boolean acmatch = Encoding.urlEncode(currAcc.getUser()).equals(currAcc.getStringProperty("name", Encoding.urlEncode(currAcc.getUser())));
            if (acmatch) {
                acmatch = Encoding.urlEncode(currAcc.getPass()).equals(currAcc.getStringProperty("pass", Encoding.urlEncode(currAcc.getPass())));
            }
            if (acmatch && ret != null && ret instanceof HashMap<?, ?>) {
                final HashMap<String, String> cookies = (HashMap<String, String>) ret;
                if (currAcc.isValid()) {
                    for (final Map.Entry<String, String> cookieEntry : cookies.entrySet()) {
                        final String key = cookieEntry.getKey();
                        final String value = cookieEntry.getValue();
                        br.setCookie(getHost(), key, value);
                    }
                    // perform a test?
                    return true;
                }
            }
            return false;
        }
    }

    private void tempUnavailableHoster(final long timeout) throws PluginException {
        if (this.currDownloadLink == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT, "Unable to handle this errorcode!");
        }
        synchronized (hostUnavailableMap) {
            HashMap<String, Long> unavailableMap = hostUnavailableMap.get(this.currAcc);
            if (unavailableMap == null) {
                unavailableMap = new HashMap<String, Long>();
                hostUnavailableMap.put(this.currAcc, unavailableMap);
            }
            /* wait 30 mins to retry this host */
            unavailableMap.put(this.currDownloadLink.getHost(), (System.currentTimeMillis() + timeout));
        }
        throw new PluginException(LinkStatus.ERROR_RETRY);
    }

    private void postAPISafe(final String accesslink, final String postdata) throws Exception {
        postAPISafe(br, accesslink, postdata);
    }

    private void postAPISafe(final Browser br, final String accesslink, final String postdata) throws Exception {
        ajax = br.cloneBrowser();
        ajax.getHeaders().put("Accept", "application/json, text/javascript, */*; q=0.01");
        ajax.getHeaders().put("X-Requested-With", "XMLHttpRequest");
        postPage(ajax, accesslink, postdata);
        handleErrors(ajax);
    }

    private void submitFormAPISafe(final Browser br, final Form form) throws Exception {
        ajax = br.cloneBrowser();
        ajax.getHeaders().put("Accept", "application/json, text/javascript, */*; q=0.01");
        ajax.getHeaders().put("X-Requested-With", "XMLHttpRequest");
        submitForm(ajax, form);
        handleErrors(ajax);
    }

    @Override
    protected void getPage(Browser ibr, String page) throws Exception {
        super.getPage(ibr, page);
        if (ibr.getRedirectLocation() != null) {
            if (!Browser.getHost(br.getRedirectLocation()).equals(this.getHost())) {
                // problem
                logger.warning("problem with hoster redirecting to another domain");
                throw new PluginException(LinkStatus.ERROR_FATAL);
            }
        }
    }

    /**
     * Handles API and old html error for failover (might be needed for download servers)
     *
     * @throws PluginException
     */
    private void handleErrors(final Browser br) throws PluginException {
        final String error = "error".equals(PluginJSonUtils.getJsonValue(br, "status")) ? PluginJSonUtils.getJsonValue(br, "message") : br.getRegex("class=\"htmlerror\"><b>(.*?)</b></span>").getMatch(0);
        if (error != null) {
            if (StringUtils.containsIgnoreCase(error, "Wrong captcha")) {
                throw new PluginException(LinkStatus.ERROR_CAPTCHA);
            } else if (StringUtils.containsIgnoreCase(error, "No premium account working")) {
                logger.warning("'No premium account working' --> Host is temporarily disabled");
                tempUnavailableHoster(1 * 60 * 60 * 1000l);
            } else if (StringUtils.containsIgnoreCase(error, "username or password is incorrect") || StringUtils.containsIgnoreCase(error, "Username or Password is invalid")) {
                /* Invalid logindata */
                if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUng??ltiger Benutzername, Passwort oder login Captcha!\r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen? Versuche folgendes:\r\n1. Falls dein Passwort Sonderzeichen enth??lt, ??ndere es (entferne diese) und versuche es erneut!\r\n2. Gib deine Zugangsdaten per Hand (ohne kopieren/einf??gen) ein.", PluginException.VALUE_ID_PREMIUM_DISABLE);
                } else {
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password or login captcha!\r\nYou're sure that the username and password you entered are correct? Some hints:\r\n1. If your password contains special characters, change it (remove them) and try again!\r\n2. Type in your username/password by hand without copy & paste.", PluginException.VALUE_ID_PREMIUM_DISABLE);
                }
            } else if (StringUtils.containsIgnoreCase(error, "Files not found")) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            } else if (StringUtils.containsIgnoreCase(error, "the daily download limit of")) {
                logger.warning("Exceeded daily limit of host");
                tempUnavailableHoster(1 * 60 * 60 * 1000l);
            } else if (StringUtils.containsIgnoreCase(error, "Host limit of")) {
                final String time = new Regex(error, "\\((.*?) remaining to get next link").getMatch(0);
                if (time != null) {
                    tempUnavailableHoster(parseTime(time));
                }
                logger.warning("Handling broken!");
                tempUnavailableHoster(20 * 60 * 1000l);
            } else if (StringUtils.containsIgnoreCase(error, "Error generating link") || StringUtils.containsIgnoreCase(error, "Get link download error")) {
                logger.warning("'Get link' error");
                handleErrorRetries("getlinkerror", 10, 2 * 60 * 1000l);
            } else if (StringUtils.equalsIgnoreCase(error, "Login to generate link")) {
                logger.warning("apparently cookies are no longer valid");
                currAcc.setProperty("cookies", Property.NULL);
                throw new PluginException(LinkStatus.ERROR_RETRY);
            } else {
                /* Unknown error */
                logger.warning("Unknown API error");
                handleErrorRetries("unknownAPIerror", 50, 2 * 60 * 1000l);
            }
        }
    }

    private long parseTime(final String time) throws PluginException {
        if (time == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        String tmpYears = new Regex(time, "(\\d+)\\s+years?").getMatch(0);
        String tmpdays = new Regex(time, "(\\d+)\\s+days?").getMatch(0);
        String tmphrs = new Regex(time, "(\\d+)\\s+hours?").getMatch(0);
        String tmpmin = new Regex(time, "(\\d+)\\s+minutes?").getMatch(0);
        String tmpsec = new Regex(time, "(\\d+)\\s+seconds?").getMatch(0);
        long years = 0, days = 0, hours = 0, minutes = 0, seconds = 0;
        if (!inValidate(tmpYears)) {
            years = Integer.parseInt(tmpYears);
        }
        if (!inValidate(tmpdays)) {
            days = Integer.parseInt(tmpdays);
        }
        if (!inValidate(tmphrs)) {
            hours = Integer.parseInt(tmphrs);
        }
        if (!inValidate(tmpmin)) {
            minutes = Integer.parseInt(tmpmin);
        }
        if (!inValidate(tmpsec)) {
            seconds = Integer.parseInt(tmpsec);
        }
        final long expires = ((years * 86400000 * 365) + (days * 86400000) + (hours * 3600000) + (minutes * 60000) + (seconds * 1000));
        return expires;
    }

    /**
     * Is intended to handle out of date errors which might occur seldom by re-tring a couple of times before we temporarily remove the host
     * from the host list.
     *
     * @param error
     *            : The name of the error
     * @param maxRetries
     *            : Max retries before out of date error is thrown
     */
    private void handleErrorRetries(final String error, final int maxRetries, final long disableTime) throws PluginException {
        if (currDownloadLink == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        int timesFailed = this.currDownloadLink.getIntegerProperty(NICE_HOSTproperty + "failedtimes_" + error, 0);
        if (timesFailed <= maxRetries) {
            logger.info(error + " -> Retrying");
            timesFailed++;
            this.currDownloadLink.setProperty(NICE_HOSTproperty + "failedtimes_" + error, timesFailed);
            throw new PluginException(LinkStatus.ERROR_RETRY, error);
        } else {
            this.currDownloadLink.setProperty(NICE_HOSTproperty + "failedtimes_" + error, Property.NULL);
            logger.info(error + " -> Disabling current host");
            tempUnavailableHoster(disableTime);
        }
    }

    @Override
    public int getMaxSimultanDownload(final DownloadLink link, final Account account) {
        return ACCOUNT_PREMIUM_MAXDOWNLOADS;
    }

    public void setConfigElements() {
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), CLEAR_DOWNLOAD_HISTORY, JDL.L("plugins.hoster.grab8com.clear_serverside_download_history", "Delete downloaded link entry from the grab8 download history after successful download?\r\n<html><b>Note that this does NOT delete the complete download history but only the entry of the SUCCESSFULLY downloaded link!</b></hml>")).setDefaultValue(default_clear_download_history));
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }

}