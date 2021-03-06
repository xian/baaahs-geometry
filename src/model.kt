import java.awt.*
import java.io.File
import java.text.ParseException
import java.util.*

data class Vertex(val x: Double, val y: Double, val z: Double, transient val lineNumber: Int? = null) {
  companion object {
    val ORIGIN = Vertex(0.0, 0.0, 0.0)
  }

  fun plus(other: Vertex) = Vertex(x + other.x, y + other.y, z + other.z)
  fun minus(other: Vertex) = Vertex(x - other.x, y - other.y, z - other.z)
  fun div(divisor: Double) = Vertex(x / divisor, y / divisor, z / divisor)

  fun distance(other: Vertex): Double {
    val xD = other.x - x
    val yD = other.y - y
    val zD = other.z - z
    return Math.sqrt(xD * xD + yD * yD + zD * zD)
  }
}

data class Face(val vertices: List<Vertex>, transient val lineNumber: Int? = null) {
  fun center(): Vertex {
    var c = Vertex.ORIGIN
    vertices.forEach { c += it }
    return c / vertices.size().toDouble()
  }
}

data class Obj(val label: String, val faces: List<Face>) {
  fun center(): Vertex {
    var c = Vertex.ORIGIN
    faces.forEach { c += it.center() }
    return c / faces.size().toDouble()
  }
}

data class Model(val objs: List<Obj>) {
  fun min(): Vertex {
    var x = Double.MAX_VALUE
    var y = Double.MAX_VALUE
    var z = Double.MAX_VALUE

    objs.forEach { obj ->
      obj.faces.forEach { face ->
        face.vertices.forEach { vertex ->
          if (vertex.x < x) x = vertex.x
          if (vertex.y < y) y = vertex.y
          if (vertex.z < z) z = vertex.z
        }
      }
    }
    return Vertex(x, y, z)
  }

  fun max(): Vertex {
    var x = Double.MIN_VALUE
    var y = Double.MIN_VALUE
    var z = Double.MIN_VALUE

    objs.forEach { obj ->
      obj.faces.forEach { face ->
        face.vertices.forEach { vertex ->
          if (vertex.x > x) x = vertex.x
          if (vertex.y > y) y = vertex.y
          if (vertex.z > z) z = vertex.z
        }
      }
    }
    return Vertex(x, y, z)
  }
}

private fun read(file: File): Model {
  val vertices = ArrayList<Vertex>()
  val faces = ArrayList<Face>()
  val objs = ArrayList<Obj>()
  var curObjName = ""

  fun addObj() {
    if (faces.isNotEmpty()) {
      objs.add(Obj(curObjName, ArrayList(faces)))
      faces.clear()
    }
  }

  var lineNumber = 0
  file.bufferedReader().forEachLine { line ->
    lineNumber++

    try {
      var words = line.trim().splitBy(" ", "\t")
      if (words.size() == 0) return@forEachLine

      val keyword = words[0]

      if (words.size() > 1) {
        words = words.subList(1, words.size())
      }

      when (keyword) {
        "#" -> {
        }

        "o" -> {
          addObj()
          curObjName = words.join(" ")
        }

        "v" -> {
          if (words.size() != 3) {
            throw RuntimeException("huh? v ${words}")
          }
          vertices.add(Vertex(words[2].toDouble(), 0 - words[1].toDouble(), words[0].toDouble(), lineNumber))
        }

        "f" -> {
          val verts = ArrayList<Vertex>()
          words.forEach {
            val vertexI = it.splitBy("/")[0].toInt()
            val vertex = if (vertexI > 0) {
              vertices.get(vertexI - 1)
            } else {
              vertices.get(vertices.size() + vertexI)
            }
            verts.add(vertex)
          }
          faces.add(Face(verts, lineNumber))
        }
      }
    } catch(e: Exception) {
      RuntimeException("Failed to parse line ${lineNumber}: ${line}", e).printStackTrace()
    }
  }

  addObj()

  return Model(objs)
}