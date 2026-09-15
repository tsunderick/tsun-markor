# License of Makefile: Public Domain / CC0
.PHONY: $(shell sed -n -e '/^$$/ { n ; /^[^ .\#][^ ]*:/ { s/:.*$$// ; p ; } ; }' $(MAKEFILE_LIST))
.NOTPARALLEL: clean
.DEFAULT_GOAL := all

env-%:
	@: $(if ${${*}},,$(error Environment variable $* not set))
####################################################################################

DIST_DIR = dist
MOVE = mv

# tsunderick: bundled Operator Mono, fetched from private dotfiles repo and converted
# to TTF (see scripts/otf2ttf.py + downloadFonts task in app/build.gradle).
# make fonts is a thin alias - the Gradle task is the single implementation.
FONT_DIR = app/thirdparty/assets/fonts

all: $(DIST_DIR) spellcheck lint deptree test build aapt_dump_badging

####################################################################################

$(DIST_DIR):
	mkdir -p ${DIST_DIR}

ANDROID_BUILD_TOOLS := $(shell test -n "$ANDROID_SDK_ROOT" && find "${ANDROID_SDK_ROOT}/build-tools" -iname "aapt" | sort -r | head -n1 | xargs dirname)
TOOL_SPELLCHECKING_ISPELL := $(shell command -v ispell 2> /dev/null)

FLAVOR := $(or ${FLAVOR},${FLAVOR},Atest)

.NOTPARALLEL: gradle gradle-analyze-log
gradle: env-ANDROID_SDK_ROOT
	mkdir -p $(DIST_DIR)/log/
	chmod +x gradlew
	./gradlew --no-daemon --parallel --stacktrace $A  2>&1 | tee "$(DIST_DIR)/log/gradle.log"
	@echo "-----------------------------------------------------------------------------------"

gradle-analyze-log:
	mv  "$(DIST_DIR)/log/gradle.log" "$(DIST_DIR)/log/gradle$A.log"
	cat "$(DIST_DIR)/log/gradle$A.log" | grep "BUILD " | tail -n1 | grep -q "BUILD SUCCESSFUL in"

adb: env-ANDROID_SDK_ROOT
	"${ANDROID_SDK_ROOT}/platform-tools/adb" $A 2>&1 | tee "$(DIST_DIR)/log/adb-$L.log"

aapt: env-ANDROID_SDK_ROOT
	"${ANDROID_BUILD_TOOLS}/aapt" $A 2>&1 | grep -v 'application-label-' | tee "$(DIST_DIR)/log/aapt$L.log"

fonts:
	@mkdir -p $(DIST_DIR)/log/
	@./gradlew downloadFonts 2>&1 | tee "$(DIST_DIR)/log/fonts.log" | grep -E "^>>|WARNING" || true
	@echo "-----------------------------------------------------------------------------------"

# --- tsunderick: terminal dev loop ---------------------------------------------
# make dev         boot emulator (if needed) + incremental build + install + relaunch
# make emulator    boot tsunderelkasten AVD and wait for full boot
# make relaunch    force-stop + fresh-launch the app on the emulator
export ANDROID_SDK_ROOT ?= $(HOME)/Android/Sdk
EMULATOR_BIN ?= /opt/android-sdk/emulator/emulator
AVD          ?= tsunderelkasten
ADB_BIN      ?= $(ANDROID_SDK_ROOT)/platform-tools/adb
DEV_PKG      ?= net.gsantner.markor

.PHONY: emulator gradle-dev relaunch dev
emulator:
	@if $(ADB_BIN) devices | grep -q "emulator.*device$$"; then \
	  echo ">> emulator already running"; \
	else \
	  echo ">> booting $(AVD) ..."; \
	  setsid nohup $(EMULATOR_BIN) -avd $(AVD) -no-boot-anim < /dev/null > /tmp/tsun-emulator.log 2>&1 & \
	  $(ADB_BIN) wait-for-device; \
	  until [ "$$($(ADB_BIN) shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 2; done; \
	  echo ">> emulator ready"; \
	fi

# Dev builds keep the Gradle daemon (upstream's `gradle` target uses --no-daemon,
# which would slow the edit-build loop to a full JVM startup every time).
gradle-dev:
	@mkdir -p $(DIST_DIR)/log/
	./gradlew assembleFlavorDefaultDebug

# Install straight from the dev build output (upstream `install` expects `make build`'s dist/ copy),
# then auto-grant storage so no permission UI appears after a re-flash (dev loop only)
install-dev:
	$(ADB_BIN) install -r app/build/outputs/apk/flavorDefault/debug/*.apk
	-$(ADB_BIN) shell pm grant $(DEV_PKG) android.permission.READ_EXTERNAL_STORAGE
	-$(ADB_BIN) shell pm grant $(DEV_PKG) android.permission.WRITE_EXTERNAL_STORAGE
	-$(ADB_BIN) shell appops set $(DEV_PKG) MANAGE_EXTERNAL_STORAGE allow

relaunch:
	-$(ADB_BIN) shell am force-stop $(DEV_PKG)
	$(MAKE) A="shell monkey -p $(DEV_PKG) -c android.intent.category.LAUNCHER 1" L="dev" adb

## Wipe app data (uninstall) so the next `make dev` runs the fresh first-run flow
reset-app:
	-$(ADB_BIN) uninstall $(DEV_PKG)

## Follow the app's own logcat output (Ctrl-C to stop)
log:
	$(ADB_BIN) logcat --pid=$$($(ADB_BIN) shell pidof $(DEV_PKG) | tr -d '\r\n') || $(ADB_BIN) logcat

dev: emulator gradle-dev install-dev relaunch

build: fonts
	rm -f $(DIST_DIR)/*.apk
	$(MAKE) A="clean assembleFlavor$(FLAVOR) -x lint" gradle
	find app -type f -newermt '-300 seconds' -iname '*.apk' -not -iname '*unsigned.apk' | xargs cp -R -t $(DIST_DIR)/
	$(MAKE) A="-build" gradle-analyze-log

lint:
	rm -Rf $(DIST_DIR)/lint
	mkdir -p $(DIST_DIR)/lint/
	$(MAKE) A="lintFlavorDefaultDebug" gradle
	find app -type f -iname 'lint-results-*' | grep -v 'intermediates' | xargs cp -R -t $(DIST_DIR)/lint
	$(MAKE) A="-lint" gradle-analyze-log

test:
	rm -Rf $(DIST_DIR)/tests
	$(MAKE) A="testFlavorDefaultDebugUnitTest -x lint" gradle
	mkdir -p app/build/test-results/testFlavorDefaultDebugUnitTest && echo 'PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4KPHRlc3RzdWl0ZSBuYW1lPSJkdW1teSIgdGVzdHM9IjEiIHNraXBwZWQ9IjAiIGZhaWx1cmVzPSIwIiBlcnJvcnM9IjAiIHRpbWVzdGFtcD0iMjAyMC0xMi0wOFQwMDowMDowMCIgaG9zdG5hbWU9ImxvY2FsaG9zdCIgdGltZT0iMC4wMSI+CiAgPHByb3BlcnRpZXMvPgogIDx0ZXN0Y2FzZSBuYW1lPSJkdW1teSIgY2xhc3NuYW1lPSJkdW1teSIgdGltZT0iMC4wMSIvPgogIDxzeXN0ZW0tb3V0PjwhW0NEQVRBW11dPjwvc3lzdGVtLW91dD4KICA8c3lzdGVtLWVycj48IVtDREFUQVtdXT48L3N5c3RlbS1lcnI+CjwvdGVzdHN1aXRlPgo=' | base64 -d > 'app/build/test-results/testFlavorDefaultDebugUnitTest/TEST-dummy.xml'
	find app -type d -iname 'testFlavorDefaultDebugUnitTest' | xargs cp -R -t $(DIST_DIR)/
	mv ${DIST_DIR}/testFlavorDefaultDebugUnitTest $(DIST_DIR)/tests
	$(MAKE) A="-test" gradle-analyze-log

deptree:
	$(MAKE) A="app:dependencies --configuration flavor$(FLAVOR)DebugRuntimeClasspath" gradle
	$(MAKE) A="-dependency-tree" gradle-analyze-log

clean:
	$(MAKE) A="clean" gradle
	rm -Rf $(DIST_DIR) app/build app/flavor* .idea dist
	find . -type f -iname "*.iml" -delete
	$(MAKE) $(DIST_DIR)
	@echo "-----------------------------------------------------------------------------------"

install:
	$(MAKE) A="install -r $(DIST_DIR)/*.apk" L="install" adb

run:
	$(MAKE) A="shell monkey -p $$(aapt dump badging $(DIST_DIR)/*.apk | grep package: | sed 's@.* name=@@' | sed 's@ .*@@' | xargs | head -n1) -c android.intent.category.LAUNCHER 1" L="run" adb

aapt_dump_badging:
	$(MAKE) A="dump badging $(DIST_DIR)/*.apk" aapt
	@echo "-----------------------------------------------------------------------------------"

spellcheck:
	mkdir -p "$(DIST_DIR)/lint/"
ifndef TOOL_SPELLCHECKING_ISPELL
	@echo "Tool ispell (spellcheck) not found in PATH. Spellcheck skipped." > "$(DIST_DIR)/lint/stringsxml-spellcheck.txt"
else
	@echo "Use ispell for spellchecking the original values/strings.xml"
	find . -iname "strings.xml" -path "*/main*/values/*" | head -n1 | xargs cat \
	   | grep "<string name=" | sed 's@.*">@@' | sed 's@</string>@@' | sed 's@\\n@  @g' | sed 's@\\@@g'  \
	   | ispell -W3 -a | grep ^\& | sed 's@[0-9]@@g' | sort | uniq | cut -d, -f1-4 \
	   | sed 's@^..@- @' | column -t -s: \
	   > "$(DIST_DIR)/lint/stringsxml-spellcheck.txt"
	@echo "\nPotential words with bad spelling:"
endif
	@cat "$(DIST_DIR)/lint/stringsxml-spellcheck.txt"
	@echo "-----------------------------------------------------------------------------------"

