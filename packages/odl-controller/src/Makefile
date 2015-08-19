
JAVA_PACKAGE = com.tailf.packages.ned.odlcontroller
JDIR = $(shell echo $(JAVA_PACKAGE) | sed 's/\./\//g')

NS  = namespaces

JFLAGS = --java-disable-prefix \
         --exclude-enums \
         --fail-on-warnings \
         --java-package $(JAVA_PACKAGE).$(NS)

all:	fxs javac
	cd ../netsim && $(MAKE) all

# Include standard NCS examples build definitions and rules
include $(NCS_DIR)/src/ncs/build/include.ncs.mk

SRC = $(wildcard yang/*.yang)
FXS = $(SRC:yang/%.yang=ncsc-out/modules/fxs/%.fxs)
JAVA_NAMESPACES = $(SRC:yang/%.yang=java/src/$(JDIR)/namespaces/%.java)

fxs:	$(FXS)

namespaces: $(JAVA_NAMESPACES) fxs

java/src/$(JDIR)/namespaces/%.java: ncsc-out/modules/fxs/%.fxs
	$(NCSC) $(JFLAGS) --emit-java $@ $<


ncsc-out/modules/fxs/tailf-ned-odl-controller-id.fxs:	yang/tailf-ned-odl-controller-id.yang
	mkdir -p ncsc-out/modules
	mkdir -p java/src/com/tailf/packages/ned/odlcontroller/namespaces/
	$(NCSC) --ncs-compile-module $< \
                --ncs-device-dir ncsc-out \
                --ncs-skip-statistics \
                --ncs-device-type generic-ned && \
	  cp $@ ../load-dir

ncsc-out/modules/fxs/tailf-ned-odl-controller.fxs:	yang/tailf-ned-odl-controller.yang
	mkdir -p ncsc-out/modules
	mkdir -p java/src/com/tailf/packages/ned/odlcontroller/namespaces/
	$(NCSC) --ncs-compile-module $< \
                --ncs-device-dir ncsc-out \
                --ncs-skip-statistics \
                --ncs-device-type generic-ned && \
	  cp $@ ../load-dir

javac:	namespaces
	if [ -d java ]; then \
	    cd java; ant -q all; \
	else \
	    true; \
	fi

clean:
	rm -rf ncsc-out/* ../load-dir/* ncsc-out/*
	rm -rf java/src/com/tailf/packages/ned/odlcontroller/namespaces/*.java
	rm -f ../jar/*.jar
	cd java && ant clean || true
	cd ../netsim && $(MAKE) clean






