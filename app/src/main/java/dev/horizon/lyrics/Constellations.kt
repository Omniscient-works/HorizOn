package dev.horizon.lyrics

/**
 * A real constellation figure: its stars (projected as seen from Earth, north up, scaled into a
 * box [width] × [height] with the longer side 1), each star's brightness as an apparent
 * magnitude (lower is brighter), and the traditional lines between stars.
 */
class Constellation(
    val name: String,
    /** The Hindi name. */
    val hindi: String,
    val width: Float,
    val height: Float,
    /** x, y pairs, in the order the figure's lines visit them. */
    val stars: FloatArray,
    val magnitudes: FloatArray,
    /** Pairs of star indexes. */
    val edges: IntArray,
) {
    val starCount: Int get() = magnitudes.size
    fun x(star: Int) = stars[star * 2]
    fun y(star: Int) = stars[star * 2 + 1]
}

/**
 * Figures for the rare constellation in the lyrics web (Lyrics_plan.md). Star positions,
 * magnitudes and lines come from d3-celestial's data (BSD-3-Clause, (c) 2015 Olaf Frohn),
 * projected for this app. Generated; edit the source data rather than these numbers.
 */
object Constellations {
    val all: List<Constellation> = listOf(
        Constellation(
            name = "Orion",
            hindi = "मृग",
            width = 0.6614f,
            height = 1.0f,
            stars = floatArrayOf(0.0362f, 0.182f, 0.1456f, 0.0f, 0.0696f, 0.0009f, 0.0f, 0.199f, 0.0751f, 0.3532f, 0.1328f, 0.4278f, 0.3753f, 0.464f, 0.6182f, 0.3377f, 0.5925f, 0.6142f, 0.6277f, 0.5902f, 0.651f, 0.486f, 0.6614f, 0.4414f, 0.6538f, 0.3779f, 0.6044f, 0.2273f, 0.5379f, 0.1665f, 0.4971f, 0.161f, 0.4636f, 0.9475f, 0.3808f, 0.7501f, 0.3193f, 0.6806f, 0.2953f, 0.3476f, 0.2473f, 0.7351f, 0.1865f, 1.0f, 0.2847f, 0.7105f),
            magnitudes = floatArrayOf(4.42f, 4.39f, 4.64f, 4.45f, 4.12f, 0.45f, 1.64f, 4.64f, 4.47f, 3.71f, 3.68f, 3.19f, 4.35f, 4.06f, 4.65f, 4.81f, 0.18f, 3.35f, 2.25f, 3.39f, 1.74f, 2.07f, 1.69f),
            edges = intArrayOf(0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 8, 9, 9, 10, 10, 11, 11, 12, 12, 7, 7, 13, 13, 14, 14, 15, 16, 17, 17, 18, 18, 6, 6, 19, 19, 5, 5, 20, 20, 21, 20, 22, 22, 18),
        ),
        Constellation(
            name = "Ursa Major",
            hindi = "सप्तर्षी",
            width = 1.0f,
            height = 0.7216f,
            stars = floatArrayOf(0.3684f, 0.146f, 0.5751f, 0.0813f, 0.5764f, 0.195f, 0.4151f, 0.2312f, 0.2491f, 0.1249f, 0.1495f, 0.0989f, 0.0f, 0.1592f, 0.4168f, 0.3578f, 0.4859f, 0.6856f, 0.4842f, 0.7216f, 0.54f, 0.4405f, 0.7223f, 0.4998f, 0.7393f, 0.4679f, 0.7967f, 0.0217f, 0.9667f, 0.0f, 0.7685f, 0.12f, 0.7872f, 0.2236f, 0.8597f, 0.2573f, 1.0f, 0.2932f, 0.9914f, 0.3171f),
            magnitudes = floatArrayOf(3.32f, 1.81f, 2.34f, 2.41f, 1.76f, 2.23f, 1.85f, 3.69f, 3.49f, 3.79f, 3.0f, 3.06f, 3.45f, 3.65f, 3.35f, 3.78f, 4.55f, 3.17f, 3.12f, 3.57f),
            edges = intArrayOf(0, 1, 1, 2, 2, 3, 3, 0, 0, 4, 4, 5, 5, 6, 3, 7, 7, 8, 8, 9, 7, 10, 10, 11, 10, 12, 1, 13, 13, 14, 14, 15, 15, 2, 2, 16, 16, 17, 17, 18, 19, 17),
        ),
        Constellation(
            name = "Cassiopeia",
            hindi = "शर्मिष्ठा",
            width = 1.0f,
            height = 0.6093f,
            stars = floatArrayOf(0.0f, 0.0f, 0.2263f, 0.3117f, 0.5133f, 0.2842f, 0.6974f, 0.6093f, 1.0f, 0.3574f),
            magnitudes = floatArrayOf(3.35f, 2.66f, 2.15f, 2.24f, 2.28f),
            edges = intArrayOf(0, 1, 1, 2, 2, 3, 3, 4),
        ),
        Constellation(
            name = "Cygnus",
            hindi = "हंस",
            width = 0.8742f,
            height = 1.0f,
            stars = floatArrayOf(0.0f, 0.8802f, 0.2465f, 0.7607f, 0.4492f, 0.5327f, 0.7091f, 0.3438f, 0.7856f, 0.077f, 0.8528f, 0.0f, 0.3277f, 0.3304f, 0.6439f, 0.732f, 0.8742f, 1.0f),
            magnitudes = floatArrayOf(3.21f, 2.48f, 2.23f, 2.86f, 3.76f, 3.8f, 1.25f, 3.89f, 3.05f),
            edges = intArrayOf(0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 6, 2, 2, 7, 7, 8),
        ),
        Constellation(
            name = "Scorpius",
            hindi = "वृश्चिक",
            width = 0.971f,
            height = 1.0f,
            stars = floatArrayOf(0.9665f, 0.2842f, 0.971f, 0.1299f, 0.9329f, 0.0f, 0.7507f, 0.2443f, 0.6693f, 0.2764f, 0.6047f, 0.3501f, 0.4707f, 0.6044f, 0.456f, 0.763f, 0.4348f, 0.9476f, 0.2981f, 0.9905f, 0.099f, 1.0f, 0.0f, 0.8881f, 0.0366f, 0.8346f, 0.1026f, 0.7429f),
            magnitudes = floatArrayOf(2.89f, 2.29f, 2.56f, 2.9f, 1.06f, 2.82f, 2.29f, 3.0f, 3.62f, 3.32f, 1.86f, 2.99f, 2.39f, 1.62f),
            edges = intArrayOf(0, 1, 1, 2, 1, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13),
        ),
        Constellation(
            name = "Leo",
            hindi = "सिंह",
            width = 1.0f,
            height = 0.4786f,
            stars = floatArrayOf(0.8381f, 0.4786f, 0.841f, 0.3168f, 0.7379f, 0.2166f, 0.3126f, 0.184f, 0.0f, 0.3654f, 0.301f, 0.3559f, 0.7611f, 0.0967f, 0.9414f, 0.0f, 1.0f, 0.0718f),
            magnitudes = floatArrayOf(1.36f, 3.48f, 2.01f, 2.56f, 2.14f, 3.33f, 3.43f, 3.88f, 2.97f),
            edges = intArrayOf(0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 0, 2, 6, 6, 7, 7, 8),
        ),
        Constellation(
            name = "Lyra",
            hindi = "स्वरमंडळ",
            width = 0.646f,
            height = 1.0f,
            stars = floatArrayOf(0.4263f, 0.2912f, 0.4346f, 0.0f, 0.646f, 0.1159f, 0.1455f, 0.3925f, 0.0f, 1.0f, 0.2712f, 0.9062f),
            magnitudes = floatArrayOf(4.34f, 4.59f, 0.03f, 4.22f, 3.25f, 3.52f),
            edges = intArrayOf(0, 1, 1, 2, 2, 0, 0, 3, 3, 4, 4, 5, 5, 0),
        ),
        Constellation(
            name = "Crux",
            hindi = "त्रिशंकु",
            width = 0.6944f,
            height = 1.0f,
            stars = floatArrayOf(0.0f, 0.4422f, 0.6944f, 0.2822f, 0.4369f, 1.0f, 0.3468f, 0.0f),
            magnitudes = floatArrayOf(1.25f, 2.79f, 0.77f, 1.59f),
            edges = intArrayOf(0, 1, 2, 3),
        ),
        Constellation(
            name = "Gemini",
            hindi = "मिथुन",
            width = 1.0f,
            height = 0.9368f,
            stars = floatArrayOf(1.0f, 0.4474f, 0.9071f, 0.4538f, 0.6658f, 0.3386f, 0.3712f, 0.0925f, 0.1273f, 0.0f, 0.0f, 0.1817f, 0.0994f, 0.245f, 0.2646f, 0.4918f, 0.4442f, 0.5628f, 0.7527f, 0.7611f, 0.6688f, 0.9368f, 0.2806f, 0.7571f),
            magnitudes = floatArrayOf(3.31f, 2.87f, 3.06f, 4.41f, 1.58f, 1.16f, 4.06f, 3.5f, 4.01f, 1.93f, 3.35f, 3.58f),
            edges = intArrayOf(0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 7, 11),
        ),
        Constellation(
            name = "Pegasus",
            hindi = "उच्चै:श्रवा",
            width = 1.0f,
            height = 0.7308f,
            stars = floatArrayOf(0.79f, 0.0f, 0.6025f, 0.0888f, 0.4806f, 0.144f, 0.0882f, 0.0725f, 0.0f, 0.4568f, 0.4636f, 0.4858f, 0.579f, 0.5685f, 0.6132f, 0.6048f, 0.8266f, 0.7308f, 1.0f, 0.6217f, 0.5606f, 0.2389f, 0.5814f, 0.2665f, 0.8192f, 0.2118f, 0.9568f, 0.1913f),
            magnitudes = floatArrayOf(4.28f, 2.93f, 2.44f, 2.07f, 2.83f, 2.49f, 4.2f, 3.41f, 3.52f, 2.38f, 3.51f, 3.97f, 3.77f, 4.14f),
            edges = intArrayOf(0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 5, 2, 2, 10, 10, 11, 11, 12, 12, 13),
        ),
        Constellation(
            name = "Taurus",
            hindi = "वृषभ",
            width = 1.0f,
            height = 0.912f,
            stars = floatArrayOf(0.0f, 0.2339f, 0.4589f, 0.4112f, 0.5119f, 0.4316f, 0.577f, 0.4394f, 0.5542f, 0.3809f, 0.5133f, 0.3303f, 0.107f, 0.0f, 0.7195f, 0.5339f, 0.98f, 0.6117f, 0.7045f, 0.7346f, 1.0f, 0.6332f, 0.9176f, 0.912f),
            magnitudes = floatArrayOf(2.97f, 0.87f, 3.4f, 3.65f, 3.77f, 3.53f, 1.65f, 3.41f, 3.73f, 3.91f, 3.61f, 4.29f),
            edges = intArrayOf(0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 3, 7, 7, 8, 8, 9, 8, 10, 10, 11),
        ),
        Constellation(
            name = "Canis Major",
            hindi = "बृहद् लुब्धक",
            width = 0.7662f,
            height = 1.0f,
            stars = floatArrayOf(0.7662f, 0.3349f, 0.4752f, 0.2591f, 0.2477f, 0.6457f, 0.1843f, 0.7861f, 0.2666f, 0.8686f, 0.3042f, 0.9249f, 0.7598f, 1.0f, 0.0f, 0.9543f, 0.332f, 0.2769f, 0.2317f, 0.1998f, 0.3574f, 0.0f),
            magnitudes = floatArrayOf(1.98f, -1.44f, 3.02f, 1.83f, 3.49f, 1.5f, 3.02f, 2.45f, 4.36f, 4.11f, 4.08f),
            edges = intArrayOf(0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 7, 3, 1, 8, 8, 9, 9, 10, 10, 8),
        ),
        Constellation(
            name = "Aquila",
            hindi = "गरुड",
            width = 0.8632f,
            height = 1.0f,
            stars = floatArrayOf(0.3334f, 0.1806f, 0.2744f, 0.2722f, 0.2148f, 0.4011f, 0.0f, 0.7814f, 0.2505f, 0.6846f, 0.6038f, 0.5738f, 0.8621f, 0.0f, 0.8632f, 1.0f),
            magnitudes = floatArrayOf(2.72f, 0.76f, 3.71f, 3.24f, 3.87f, 3.36f, 2.99f, 3.43f),
            edges = intArrayOf(0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 1, 1, 5, 5, 7),
        ),
        Constellation(
            name = "Delphinus",
            hindi = "तिमि",
            width = 0.6766f,
            height = 1.0f,
            stars = floatArrayOf(0.6766f, 1.0f, 0.4539f, 0.318f, 0.3491f, 0.0452f, 0.0f, 0.0f, 0.158f, 0.2185f),
            magnitudes = floatArrayOf(4.03f, 3.64f, 3.77f, 5.15f, 4.43f),
            edges = intArrayOf(0, 1, 1, 2, 2, 3, 3, 4, 4, 1),
        ),
    )
}
