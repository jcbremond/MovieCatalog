package org.andreaiacono.moviecatalog.activity

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.*
import org.andreaiacono.moviecatalog.R
import org.andreaiacono.moviecatalog.activity.task.FileSystemImageLoaderTask
import org.andreaiacono.moviecatalog.activity.task.NasScanningTask
import org.andreaiacono.moviecatalog.core.MoviesCatalog
import org.andreaiacono.moviecatalog.model.Movie
import org.andreaiacono.moviecatalog.model.NasMovie
import org.andreaiacono.moviecatalog.ui.AsyncTaskType
import org.andreaiacono.moviecatalog.ui.PostTaskListener
import org.andreaiacono.moviecatalog.util.ImageAdapter
import org.andreaiacono.moviecatalog.util.MovieBitmap
import org.andreaiacono.moviecatalog.util.thumbNameNormalizer
import java.io.Serializable
import android.view.Gravity
import android.view.View
import android.widget.TextView
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.andreaiacono.moviecatalog.model.Config
import android.app.ActivityManager
import android.widget.Toast
import android.widget.AdapterView

class MainActivity : PostTaskListener<Any>, AppCompatActivity() {

    val LOG_TAG = this.javaClass.name

    private lateinit var config: Config
    private lateinit var gridView: GridView
    private lateinit var genresListView: ListView
    private lateinit var moviesCatalog: MoviesCatalog
    private lateinit var movieBitmaps: ArrayList<MovieBitmap>
    private lateinit var genresAdapter: ArrayAdapter<String>
    private lateinit var imageAdapter: ImageAdapter

    override fun onPostTask(result: Any, asyncTaskType: AsyncTaskType, exception: Exception?) {

        if (exception != null) {
            Log.d(LOG_TAG, exception.message, exception)
            longToast("An error occurred while executing $asyncTaskType: ${exception.message}")
        }
        else
            when (asyncTaskType) {
                AsyncTaskType.NAS_SCAN -> {
                    val progressBar: ProgressBar = findViewById(R.id.indefiniteProgressBar)
                    val newMovies = (result as List<NasMovie>)
                        .map {
                            Movie(
                                it.title,
                                it.sortingTitle!!,
                                it.date,
                                it.dirName,
                                it.videoFilename,
                                it.genres,
                                thumbNameNormalizer(it.title),
                                getInfoFromNasMovie(it)
                            )
                        }
                        .toList()
                    if (newMovies.isEmpty()) {
                        shortToast("No new movies found")
                    }
                    else {
                        val movies = moviesCatalog.movies.toMutableList()
                        longToast("New movies found: ${newMovies.size}")
                        movies.addAll(newMovies)
                        moviesCatalog.movies = movies
                        moviesCatalog.updateGenres()
                        moviesCatalog.saveCatalog()
                        FileSystemImageLoaderTask(this, moviesCatalog, progressBar).execute()
                    }
                }
                AsyncTaskType.FILE_SYSTEM_IMAGE_LOAD -> {
                    movieBitmaps = result as ArrayList<MovieBitmap>
                    if (!movieBitmaps.isEmpty()) {
                        imageAdapter = ImageAdapter(this, movieBitmaps)
                        imageAdapter.setDateComparator()
                        gridView.adapter = imageAdapter
                        genresAdapter.notifyDataSetChanged()
                    }
                }
                AsyncTaskType.DUNE_HD_COMMANDER -> {
                    shortToast(result.toString())
                }
                else -> {
                }
            }
    }

    private fun getInfoFromNasMovie(movie: NasMovie) = movie.title + " " +
                                                       movie.directors.joinToString { it } + " " +
                                                       movie.cast.joinToString { it } + " " +
                                                       movie.year


    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)

        config = loadConfig();
        moviesCatalog = MoviesCatalog(
            this,
            config.nasUrl,
            config.duneIp
        )

        val toolbar: Toolbar = findViewById(R.id.mainToolbar)
        setSupportActionBar(toolbar)

        genresListView = findViewById(R.id.genresListView)
        genresAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, moviesCatalog.genres)
        genresListView.adapter = genresAdapter
        genresListView.choiceMode = ListView.CHOICE_MODE_SINGLE
        genresListView.setSelector(R.color.darkMenuBackground)
        genresListView.onItemClickListener = AdapterView.OnItemClickListener { _, _, i, _ ->
            imageAdapter.filterByGenre(genresListView.adapter.getItem(i).toString())
        }

        gridView = findViewById(R.id.moviesGridView)

        val scaleFactor = resources.displayMetrics.density * 180
        val number = windowManager.defaultDisplay.width
        val columns = (number.toFloat() / scaleFactor).toInt()
        gridView.numColumns = columns

        gridView.onItemClickListener = AdapterView.OnItemClickListener { _, v, position, _ ->
            val fullScreenIntent = Intent(v.context, MovieDetailActivity::class.java)
            fullScreenIntent.putExtra("movie", (imageAdapter.getItem(position) as MovieBitmap).movie)
            fullScreenIntent.putExtra("NasService", moviesCatalog.nasService as Serializable)
            fullScreenIntent.putExtra("DuneHdService", moviesCatalog.duneHdService as Serializable)
            startActivity(fullScreenIntent)
        }

        val fileSystemProgressBar: ProgressBar = findViewById(R.id.horizontalProgressBar)
        FileSystemImageLoaderTask(this, moviesCatalog, fileSystemProgressBar).execute()

        if (moviesCatalog.hasNoData) {
            val builder = AlertDialog.Builder(this)
            builder.setMessage("No movies found on this device.\n\nScan your NAS!")
            builder.setCancelable(false)
            builder.setPositiveButton("Close", null)
            val dialog = builder.show()
            val messageView = dialog.findViewById<View>(android.R.id.message) as TextView
            messageView.gravity = Gravity.CENTER
        }
    }

    fun loadConfig(): Config {
        val mapper = ObjectMapper(YAMLFactory())
        return mapper.readValue(resources.openRawResource(R.raw.config), Config::class.java)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)

        val searchView = menu.findItem(R.id.action_search).actionView as SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {

            override fun onQueryTextSubmit(s: String): Boolean {
                imageAdapter.search(s)
                return false
            }

            override fun onQueryTextChange(s: String): Boolean {
                imageAdapter.search(s)
                return false
            }
        })

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        return when (item.itemId) {
            R.id.action_scan -> {
                val horizontalProgressBar: ProgressBar = findViewById(R.id.horizontalProgressBar)
                NasScanningTask(this, moviesCatalog, horizontalProgressBar).execute()
                true
            }
            R.id.action_info -> {
                val builder = AlertDialog.Builder(this)
                val message = "Ip Address Dune HD: ${config.duneIp}" +
                        "\nNAS Url: ${config.nasUrl}" +
                        "\nSaved Movies: " + moviesCatalog.getCount()
                builder.setMessage(message).setTitle(R.string.info_title)
                val dialog = builder.create()
                dialog.show()
                true
            }
            R.id.action_delete -> {
                moviesCatalog.deleteAll()
                moviesCatalog = MoviesCatalog(applicationContext, config.nasUrl, config.duneIp)
//                imageAdapter = ImageAdapter(applicationContext, listOf())
//                imageAdapter.notifyDataSetChanged()
                shortToast("All files deleted")
                true
            }
            R.id.action_debugInfo -> {
                val sendIntent: Intent = Intent().apply {
                    val activityManager = applicationContext.getSystemService(ACTIVITY_SERVICE) as ActivityManager
                    val memoryInfo = ActivityManager.MemoryInfo()
                    activityManager.getMemoryInfo(memoryInfo)

                    action = Intent.ACTION_SEND
                    val files =
                        "Config: $config\n\nMemory info: ${memoryInfo.lowMemory}\n\nPrivate directory content: \n${filesDir.list().map { "[$it]" }.joinToString(
                            "\n"
                        )}"
                    putExtra(Intent.EXTRA_TEXT, files)
                    type = "text/plain"
                    Log.d(LOG_TAG, files)
                }
                startActivity(sendIntent)
                true
            }
            R.id.menuSortByTitle -> {
                imageAdapter.setTitleComparator()
                true
            }
            R.id.menuSortByDate -> {
                imageAdapter.setDateComparator()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun shortToast(message: String) {
        toast(message, Toast.LENGTH_SHORT)
    }

    private fun longToast(message: String) {
        toast(message, Toast.LENGTH_LONG)
    }

    private fun toast(message: String, length: Int) {
        val toast = Toast.makeText(applicationContext, message, length)
        toast.show()
    }
}
