package com.example.platform

/**
 * Which phone this code is running on.
 *
 * The first thing that was put in the shared module, back when it held
 * nothing else and existed only to give both compilers something to build
 * while the toolchain underneath them was proved out. The screens, the
 * database and the stats have since followed it here.
 *
 * Nothing calls this now. It is kept as the smallest working example of an
 * expect/actual pair, which is the one piece of Kotlin Multiplatform syntax
 * every other platform file in this module depends on.
 */
expect fun platformName(): String
