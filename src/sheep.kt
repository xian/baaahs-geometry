import javafx.geometry.Point3D
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import kotlin.text.Regex

class Sheep(val model: Model) {
  val panels: MutableList<Panel> = arrayListOf()

  init {
    panels.addAll(model.objs.map { Panel(it.label, it.faces) })
  }
}

data class Panel(val name: String, private val faces: List<Face>) {
  val outerEdges: List<Edge>
    get() {
      val edges = hashSetOf<Edge>()
      var expected = 0
      faces.map { face ->
        if (name == "19P") println("face!")
        val vertexCount = face.vertices.size()
        face.vertices.forEachIndexed { i, vertex ->
          val edge = Edge(face.vertices[i], face.vertices[(i + 1) % vertexCount])
          if (name == "19P") println(edge)
          if (!edges.add(edge)) edges.remove(edge) // if it's already present, this is an interior edge
          expected++
        }
      }
      if (edges.size() != expected) {
        println("Eliminated duplicate edges for ${name}!")
      }
      return edges.toList()
    }
}

data class Edge(val a: Vertex, val b: Vertex) {
  transient val length: Double
    get() = a.distance(b)

  override fun equals(other: Any?): Boolean {
    if (other is Edge) {
      return other.a == a && other.b == b
          || other.b == a && other.a == b
    }
    return super.equals(other)
  }

  override fun hashCode(): Int {
    return a.hashCode() + b.hashCode()
  }
}

class EdgeCalculator(val sheep: Sheep) {
  fun dumpEdges(csv: CSV) {
    csv.add("Panel", "Edge Count", "Edge Lengths")
    sheep.panels.forEach { panel ->
      val edgeLengths = panel.outerEdges.map { it.length }.sortDescending().map { java.lang.String.format("%.2f", it / 12) }
      val args = arrayListOf(panel.name, edgeLengths.size()).let { it.addAll(edgeLengths); it }
      csv.add(*args.toArray())
    }
  }
}

fun Double.toFeetAndInches(): String {
  val feet = Math.floor(this / 12).toInt()
  val inches = this - feet * 12
  return java.lang.String.format("%d'%.2f\"", feet, inches)
}

class CSV(file: File) {
  val out = file.printWriter().buffered()
  final val SPECIAL_CHARS = Regex(".*[,\"\n].*")

  fun add(vararg items: Any) {
    var index = 0

    items.forEach {
      if (index++ > 0) out.append(",")

      if (it is String && it.matches(SPECIAL_CHARS)) {
        out.append("\"${it.replace("\"", "\"\"")}\"")
      } else {
        out.append(it.toString())
      }
    }

    out.append("\n")
  }

  fun close() {
    out.close()
  }
}

