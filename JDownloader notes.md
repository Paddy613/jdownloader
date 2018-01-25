When built:

# Code
- JDownloader.jar - downloads, installs, updates, and launches the main app. This *is* required to run JDownloader. - see below
- Core.jar - see below
- jd/plugins - built from jdownloader/src/jd/plugins
- libs - see below
- extensions - built from jdownloader/src/org/jdownloader/extensions

## JDownloader.jar breakdown
- org/tukaani/xz - http://tukaani.org/xz/java.html
- org/jdownloader/update - NO SOURCE
- org/jdownloader/updatev2 - built from jdownloader/src/org/jdownloader/updatev2
- org/jdownloader/uninstaller - NO SOURCE
- org/jdownloader/installer - NO SOURCE
- org/jdownloader/logging - built from jdownloader/src/org/jdownloader/logging
- org/appwork - built from jdownloader/build/AppWorkUtils/src/org/appwork
- net/miginfocom - http://www.miglayout.com/
- lib - proxy_util\_\*.dll
- com/btr/proxy - modified version of proxyVole https://github.com/brsanthu/proxy-vole - NO SOURCE 
- jd/Main.class - maaaybe built from jdownloader/src/org/jdownloader/startup/Main.java ?? The other files in jdownloader/src/org/jdownloader/startup/ aren't represented here… — NO SOURCE??
### Themeing and configuration files
- themes/
- cfg/
- version.nfo
- classpath
- proxyVole.info

## Core.jar breakdown
- jd - built from jdownloader/src/jd (plugins are built into jd/plugins)
- javax - built from jdownloader/src/javax
- org - built from jdownloader/src/org

## libs/ breakdown

### Code
- JDUtils.jar - built from jdownloader/src/jd/nutils
- JDHttp.jar - built from jd-browser/src/jd/http
- JDGUI.jar - built from jdownloader/src/jd/gui
- JAntiCaptcha.jar - built from jdownloader/src/jd/captcha
- Dynamics.jar - contains only a MANIFEST.MF
- cons.jar - built from jdownloader/src/org/jdownloader/container

### Bundled libs (copied from libs/ or jdownloader/ressourcen/libs (with a !))

#### Dirs
- UPNP/ ! - https://github.com/4thline/cling and https://github.com/4thline/seamless
- dbus/ ! - https://github.com/diega/libmatthew-java and https://dbus.freedesktop.org/releases/dbus-java/
- laf/ ! - NO SOURCE - http://www.javasoft.de/synthetica/license/

#### Jars
- zip4j ! - http://www.lingala.net/zip4j/
- svgSalamander - https://github.com/blackears/svgSalamander
- sevenzipjbindingLinux ! - http://sevenzipjbind.sourceforge.net/
- sevenzipjbinding ! - http://sevenzipjbind.sourceforge.net/
- proxyVole - https://github.com/brsanthu/proxy-vole - NO SOURCE if this is modified from vanilla, which proxyVole.info in JDownloader.jar seems to indicate, although that might only encompass the code from it included in that jar file.
- jsyntaxpane ! - some version of https://github.com/nordfalk/jsyntaxpane (there's a more up-to-date fork with a different name at https://github.com/Sciss/SyntaxPane)
- js ! - https://developer.mozilla.org/en-US/docs/Mozilla/Projects/Rhino/Download_Rhino
- jna ! - https://github.com/java-native-access/jna
- jna_platform ! - https://github.com/java-native-access/jna
- jackson-databind - https://github.com/FasterXML/jackson-databind
- jackson-core - https://github.com/FasterXML/jackson-core
- jackson-annotations - https://github.com/FasterXML/jackson-annotations
- image4j ! - https://github.com/imcdonagh/image4j
- htmlunit-core-js ! - https://github.com/HtmlUnit/htmlunit-core-js
- Filters ! - http://www.jhlabs.com/ip/filters/Filters.zip (I can't access this — DNS problem — as of 2017 Apr. 02; the Wayback Machine Save Page feature works on it, though.)
- bcprov-jdk15on - http://www.bouncycastle.org/latest_releases.html

# mystery stuff
- tmp - ???? (contains a .so file)

# other folders
- cfg
- logs
- java (empty)
- translations
- tools (rtmpdump binary)
- themes
- jd/captcha (captcha recognition samples and stuff)
- licenses
- update

# extra little files - 
- build.json
- license*.txt
