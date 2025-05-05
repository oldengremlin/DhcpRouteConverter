#!/bin/sh

CDIR=$( pwd )
cd /home/olden/NetBeansProjects/DhcpRouteConverter

mkdir -p /tmp/dhcprouteconverter

(
for F in $( find src/ -type f -print ) ./pom.xml ./nb-configuration.xml ./README.md routers.yaml; do
    echo "================================================================================"
    echo "===" ${F};
    echo "================================================================================"
    cat ${F}
    echo
done
) | tee /tmp/dhcprouteconverter/source.txt

#tar -czf /tmp/dhcprouteconverter/source.tar.gz $( find src/ -type f -print ) ./pom.xml ./nb-configuration.xml ./README.md
#base64 /tmp/dhcprouteconverter/source.tar.gz > /tmp/dhcprouteconverter/source.targz.b64.txt

cd ${CDIR}
