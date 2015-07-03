import java.awt.*
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.geom.Point2D
import java.io.File
import java.util.*

fun main(args: Array<String>) {
  val file = File("/Users/xian/IdeaProjects/baaahs-labels/model.obj")

  //  println("${model.faces.size()} faces: ${model.faces}")

  var near = true
  var pickProjection: (Model) -> Projection = { FrontProjection(it, near) }

  val frame = Frame(file.name)
  frame.setSize(600, 600)
  frame.setVisible(true)
  frame.setLayout(BorderLayout())

  var model = read(file)
  var view: ModelView? = null

  fun refreshView() {
    if (view != null) {
      frame.remove(view)
    }

    val projection = pickProjection(model)
    println("projection = ${projection}")
    view = ModelView(model, projection)
    frame.add(view, BorderLayout.CENTER)
    frame.validate()
  }

  refreshView()

  frame.addWindowFocusListener(object : WindowAdapter() {
    override fun windowGainedFocus(e: WindowEvent?) {
      model = read(file)
      refreshView()
    }
  })

  val controls = Container()
  controls.setLayout(FlowLayout(FlowLayout.CENTER))

  val sideButton = Button("Side")
  sideButton.addActionListener({ event -> pickProjection = { SideProjection(it, near) }; refreshView() })
  controls.add(sideButton)

  val frontButton = Button("Front")
  frontButton.addActionListener({ event -> pickProjection = { FrontProjection(it, near) }; refreshView() })
  controls.add(frontButton)

  val topButton = Button("Top")
  topButton.addActionListener({ event -> pickProjection = { TopProjection(it, near) }; refreshView() })
  controls.add(topButton)

  val perspectiveButton = Button("Perspective")
  perspectiveButton.addActionListener({ event -> pickProjection = { PerspectiveProjection(it, Vertex(15.0, 15.0, 100.0)) }; refreshView() })
  controls.add(perspectiveButton)

  val nearButton = Button("Near")
  nearButton.addActionListener({ event -> near = true; refreshView() })
  controls.add(nearButton)

  val farButton = Button("Far")
  farButton.addActionListener({ event -> near = false; refreshView() })
  controls.add(farButton)

  frame.add(controls, BorderLayout.SOUTH)
}

interface Projection {
  fun project(vertex: Vertex): Point2D
  fun distance(vertex: Vertex): Double
  fun isVisible(vertex: Vertex): Boolean
}

class PerspectiveProjection(val model: Model, val eye: Vertex) : Projection {
  val modelMin = model.min()
  val modelMax = model.max()
  val modelAbs = modelMax - modelMin

  override fun project(vertex: Vertex): Point2D.Double {
    val normalized = Vertex(normalizeX(vertex), normalizeY(vertex), normalizeZ(vertex))

    return Point2D.Double(Sx(normalized), Sy(normalized))
  }

  private fun normalizeX(vertex: Vertex) = (vertex.x - modelMin.x) / modelAbs.x
  private fun normalizeY(vertex: Vertex) = (vertex.y - modelMin.y) / modelAbs.y
  private fun normalizeZ(vertex: Vertex) = (vertex.z - modelMin.z) / modelAbs.z

  // ----------------------------------------
  // Formula to solve Sx
  // ----------------------------------------
  // Ez = distance from eye to the center of the screen
  // Ex = X coordinate of the eye
  // Px = X coordinate of the 3D point
  // Pz = Z coordinate of the 3D point
  //
  //              Ez*(Px-Ex)
  // Sx  = -----------------------  + Ex
  //                Ez+Pz
  fun Sx(p: Vertex): Double {
    return (eye.z * (p.x - eye.x)) / (eye.z + p.z) + eye.x
  }

  // ----------------------------------------
  // Formula to solve Sy
  // ----------------------------------------
  // Ez = distance from eye to the center of the screen
  // Ey = Y coordinate of the eye
  // Py = Y coordinate of the 3D point
  // Pz = Z coordinate of the 3D point
  //
  //            Ez*(Py-Ey)
  // Sy  = -------------------  + Ey
  //              Ez+Pz
  fun Sy(p: Vertex): Double {
    return (eye.z * (p.y - eye.y)) / (eye.z + p.z) + eye.y
  }

  override fun distance(vertex: Vertex): Double {
    return 1.0
  }

  override fun isVisible(vertex: Vertex): Boolean {
    return true
  }

}

class SideProjection(val model: Model, val near: Boolean) : Projection {
  val modelMin = model.min()
  val modelMax = model.max()
  val modelAbs = modelMax - modelMin
  val middleDistance = modelAbs.z / 2 + modelMin.z

  override fun project(vertex: Vertex): Point2D.Double {
    return Point2D.Double(
        (vertex.x - modelMin.x) / modelAbs.x,
        (vertex.y - modelMin.y) / modelAbs.y)
  }

  override fun distance(vertex: Vertex): Double {
    val dist = if (near) vertex.z - modelMin.z else modelMax.z - vertex.z
    return dist / modelAbs.z
  }

  override fun isVisible(vertex: Vertex): Boolean {
    return if (near) vertex.z > middleDistance else vertex.z < middleDistance
  }
}

class TopProjection(val model: Model, val near: Boolean) : Projection {
  val modelMin = model.min()
  val modelMax = model.max()
  val modelAbs = modelMax - modelMin
  val middleDistance = modelAbs.y / 2 + modelMin.y

  override fun project(vertex: Vertex): Point2D.Double {
    return Point2D.Double(
        (vertex.x - modelMin.x) / modelAbs.x,
        (modelMax.z - vertex.z) / modelAbs.z)
  }

  override fun distance(vertex: Vertex): Double {
    return (vertex.y - modelMin.y) / modelAbs.y
  }

  override fun isVisible(vertex: Vertex): Boolean {
    return if (near) vertex.y > middleDistance else vertex.y < middleDistance
  }
}

class FrontProjection(val model: Model, val near: Boolean) : Projection {
  val modelMin = model.min()
  val modelMax = model.max()
  val modelAbs = modelMax - modelMin
  val middleDistance = modelAbs.x / 2 + modelMin.x

  override fun project(vertex: Vertex): Point2D.Double {
    return Point2D.Double(
        (vertex.z - modelMin.z) / modelAbs.z,
        (vertex.y - modelMin.y) / modelAbs.y)
  }

  override fun distance(vertex: Vertex): Double {
    return (vertex.x - modelMin.x) / modelAbs.x
  }

  override fun isVisible(vertex: Vertex): Boolean {
    return if (near) vertex.x > middleDistance else vertex.x < middleDistance
  }
}

class Drawable(val distance: Double, val runnable: () -> Unit)

class ModelView(val model: Model, val projection: Projection) : Component() {
  override fun paint(g: Graphics) {
    val faceFont = g.getFont()
    val faceFontFM = g.getFontMetrics(faceFont)

    val groupFont = faceFont.deriveFont(Font.BOLD, faceFont.getSize() * 1.2f)
    val groupFontFM = g.getFontMetrics(groupFont)

    val random = Random(0)

    val width = getWidth()
    val height = getHeight()

    fun tX(p: Point2D): Int = (p.getX() * (width - 60) + 30).toInt()
    fun tY(p: Point2D): Int = (p.getY() * (height - 60) + 30).toInt()

    g.clearRect(0, 0, width, height)

    fun rand() = random.nextFloat() * 0.7f + 0.25f

    val drawables = ArrayList<Drawable>()

    model.groups.forEach { group ->
      val groupColor = Color(rand(), rand(), rand())

      var nearest = Double.MAX_VALUE
      group.faces.forEachIndexed { i, face ->
        val polygon = Polygon()
        face.vertices.forEach { vertex ->
          val point = projection.project(vertex)
          polygon.addPoint(tX(point), tY(point))
        }

        val distance = projection.distance(face.center())
        if (distance < nearest) nearest = distance

        val fillColor = fade(groupColor, distance)
        val strokeColor = fade(Color.BLACK, distance)
        val center = projection.project(face.center())

        drawables.add(Drawable(distance, {
          g.setColor(fillColor)
          g.fillPolygon(polygon)

          g.setColor(strokeColor)
          g.drawPolygon(polygon)

          val s = "#${face.lineNumber}"
          g.setFont(faceFont)
          g.drawString(s,
              tX(center) - faceFontFM.stringWidth(s) / 2,
              tY(center) + faceFontFM.getHeight() / 2)
        }))
      }

//      if (projection.isVisible(group.center())) {
        drawables.add(Drawable(nearest - 0.00001, {
          g.setFont(groupFont)

          g.setColor(Color.BLACK)
          val center = projection.project(group.center())
          g.drawString(group.label,
              tX(center) - groupFontFM.stringWidth(group.label) / 2,
              tY(center) - groupFontFM.getHeight() / 2)
        }))
//      }
    }

    drawables.sortBy { 0 - it.distance }.forEach { it.runnable() }

    g.setFont(faceFont)
  }

  fun fade(color: Color, distance: Double): Color {
    val clarity = 1 - distance * 0.8
    val fogginess = distance * 0.8 + 0.2

    fun fade(value: Int): Int {
      return Math.min(255, (value * clarity + 200 * fogginess).toInt())
    }
    return Color(fade(color.getRed()), fade(color.getGreen()), fade(color.getBlue()))
  }
}
