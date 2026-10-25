package dev.jairoserrano.solardream

object Fixtures {
    fun load(name: String): ByteArray {
        val stream = checkNotNull(Fixtures::class.java.classLoader.getResourceAsStream("fixtures/$name")) {
            "falta el fixture $name"
        }
        return stream.use { it.readBytes() }
    }
}

