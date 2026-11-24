val name = "speculation"

rootProject.name = name

include("$name-core", "$name-plugin")
includeBuild("../Kolor")
