package com.example.platform

/**
 * Which phone this code is running on.
 *
 * The first thing in the shared module, and for now the only thing: it exists
 * so the Android and iPhone compilers both have something to build while the
 * toolchain underneath them is proved out. The screens, the database and the
 * stats follow once it is.
 */
expect fun platformName(): String
