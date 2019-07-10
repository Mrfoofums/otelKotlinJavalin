FROM openjdk:8
COPY build/distributions/ /usr/src/kotlin-1pager
WORKDIR /usr/src/kotlin-1pager
RUN ls -al
RUN tar -xf kotlin-1pager.tar
WORKDIR /usr/src/kotlin-1pager/kotlin-1pager-1.0/bin
CMD ./kotlin-1pager
