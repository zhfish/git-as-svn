# Overview

[![Join the chat at https://gitter.im/bozaro/git-as-svn](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/bozaro/git-as-svn?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

[![Build Status](https://travis-ci.org/bozaro/git-as-svn.svg?branch=master)](https://travis-ci.org/bozaro/git-as-svn)

Subversion frontend server for git repository (in Java).

## Python proof-of-concept implementation:

 * http://git.q42.co.uk/git_svn_server.git

## SVN protocol description

 * http://svn.apache.org/repos/asf/subversion/trunk/subversion/libsvn_ra_svn/protocol
 * http://svn.apache.org/repos/asf/subversion/trunk/notes/

## GitLab integration

Now we support limited GitLab integration (see config-gitlab.example):

 * Load repository list from GitLab on startup (no dynamically update yet)
 * Authentication via GitLab API

### git-lfs-authenticate

For support SSO git-lfs authentication you need to create file ```/usr/local/bin/git-lfs-authenticate``` with content:

```
#!/bin/sh
# TOKEN - token parameter in !lfs section
# BASE  - base url
TOKEN=secret
BASE=http://localhost:8123
curl -s -d "token=${TOKEN}" -d "external=${GL_ID}" ${BASE}/$1/info/lfs/auth
```

# How to use

## Run from binaries

For quick run you need:

 * Install Java 1.8 or later
 * Download binaries archive from: https://github.com/bozaro/git-as-svn/releases/latest
 * After unpacking archive you can run server executing:<br/>
   `java -jar git-as-svn.jar --config config.example --show-config`
 * Test connection:<br/>
   `svn ls svn://localhost/example`<br/>
   with login/password: test/test

As result:

 * Server creates bare repository with example commit in directory: `example.git`
 * The server will be available on svn://localhost/example/ url (login/password: test/test)

## Build from sources

To build from sources you need install JDK 1.8 or later and run build script.

For Linux:

    ./gradlew deployZip

For Windows:

    call gradlew.bat deployZip

When build completes you can run server executing:

    java -jar build/deploy/git-as-svn.jar --config config.example --show-config
