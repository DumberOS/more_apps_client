package com.dumbermini.apps.data.model

@JvmInline
value class PackageName(val name: String)

fun String.toPackageName() = PackageName(this)
