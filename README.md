# snailgun :snail: :gun:

[![Build Status](https://travis-ci.org/jvican/snailgun.svg?branch=master)](https://travis-ci.org/jvican/snailgun)
![Maven Central](https://img.shields.io/maven-central/v/me.vican.jorge/snailgun-core_2.12.svg)

Snailgun is a [Nailgun](https://github.com/facebook/nailgun) client written in Scala.

The goal of snailgun is to be a flexible, cross-platform, zero-dependency
Nailgun client that can be used both as a *binary* and as a *library
dependency*.

## Motivation

Nailgun is a useful protocol to communicate lightweight, short-lived clients
with long-running servers.

Developers have traditionally used Nailgun to communicate native-like clients
with services running on the JVM. However, there are many use cases that
require JVM clients connect to Nailgun servers and those are currently unsupported.

Snailgun is an alternative implementation to the [default Python and C
clients](https://github.com/facebook/nailgun/tree/master/nailgun-client) of
the Nailgun protocol that intends to be extensible, fast and support
**both** JVM and Native clients.

* It provides a simple API that for any JVM-based programming language.
* It can be compiled to a Native binary that is 10x faster than the Python client through [GraalVM's Native Image][graalvm-native].

Snailgun's major use cases are:

1. You need a client that talks the Nailgun protocol but you need to customize it.
1. You need to communicate with a Nailgun server implemented in another language.
1. You need to communicate a JVM client with a Nailgun server on the JVM. For
   example, if the server cannot be compiled to native or synchronizes
   concurrent clients.

## Usage :wrench:

### Library
The API is meant to be simple and extensible. The
[`snailgun-core`](snailgun-core/) directory that hosts the implementation. The
entrypoint is `Client` and the full protocol implementation is `Protocol`.


Snailgun is published to Maven Central. Add it to your project with:

```scala
libraryDependencies += "me.vican.jorge" %% "snailgun" % "SNAILGUN_VERSION"
```

where `SNAILGUN_VERSION` is the latest git tag in this repository.

### Binary

You can generate a Snailgun binary by following the steps described in the
[GraalVM Native Image guide][graalvm-native].

To generate a native binary out of this repository, run
`snailgun-cli/graalvm-native-image:packageBin` in a machine that has GraalVM
and `native-image` installed.

[graalvm-native]: https://www.graalvm.org/docs/reference-manual/aot-compilation/
