#!/bin/sh

CDIR=$( pwd )
cd /home/olden/NetBeansProjects/DhcpRouteConverter

SOURCENAME=source.code.txt
cp /dev/null ${SOURCENAME}
echo "Вміст файлу source.txt:" > /dev/stderr
for F in $( find src/ -type f -name '*.java' -print ); do
    echo "/*" >>${SOURCENAME}
    echo -n "• ${F} строки з "`wc -l ${SOURCENAME} | awk '{ printf("%s",$1) }'`" по " > /dev/stderr
    echo " * File: " ${F} >>${SOURCENAME}
    echo " */" >>${SOURCENAME}
    cat ${F} | sed "/^ *$/d" >>${SOURCENAME}
    echo `wc -l ${SOURCENAME} | awk '{ printf("%s",$1) }'` > /dev/stderr #'
    echo >>${SOURCENAME}
    sed -i "/^ *$/d" ${SOURCENAME}
done

echo "" > /dev/stderr

SOURCENAME=source.txt
cp /dev/null ${SOURCENAME}
echo "Вміст файлу ${SOURCENAME}:" > /dev/stderr
for F in $( find src/deb/control/ -type f  -print ) $( find src/main/resources/ -type f -print ) $( find .github/ -type f  -print ) $( find src/main/resources/ -type f -print ) ./pom.xml ./nb-configuration.xml ./README.md ./routers.yaml; do
    echo -n "• ${F} строки з "`wc -l ${SOURCENAME} | awk '{ printf("%s",$1) }'`" по " > /dev/stderr
    echo "// File: " ${F} >>${SOURCENAME}
    cat ${F} | sed "/^ *$/d" >>${SOURCENAME}
    echo `wc -l ${SOURCENAME} | awk '{ printf("%s",$1) }'` > /dev/stderr #'
    echo >>${SOURCENAME}
    sed -i "/^ *$/d" ${SOURCENAME}
done

cd ${CDIR}
