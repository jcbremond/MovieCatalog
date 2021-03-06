package org.andreaiacono.moviecatalog.task

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.AsyncTask
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import org.andreaiacono.moviecatalog.service.MoviesCatalog
import org.andreaiacono.moviecatalog.model.EMPTY_MOVIE
import org.andreaiacono.moviecatalog.util.MovieBitmap

internal class DeviceImageLoaderTask(taskListener: PostTaskListener<Any>, val moviesCatalog: MoviesCatalog, val progressBar: ProgressBar) : AsyncTask<String, Int, Void>() {

    private val syncTaskType: AsyncTaskType = AsyncTaskType.DEVICE_IMAGE_LOAD

    val LOG_TAG = this.javaClass.name
    private var exception: Exception? = null
    private var movieBitmaps: MutableList<MovieBitmap> = mutableListOf()
    private var postTaskListener: PostTaskListener<Any> = taskListener

    override fun onPreExecute() {
        super.onPreExecute()
        progressBar.max = moviesCatalog.getCount()
        progressBar.bringToFront()
        progressBar.visibility = View.VISIBLE
    }

    override fun doInBackground(vararg dirName: String): Void? {
        moviesCatalog.movies.forEachIndexed {index, movie ->
            try {
                val filename = "${moviesCatalog.context.filesDir}/${movie.deviceThumbName}"
                movieBitmaps.add(MovieBitmap(movie, BitmapFactory.decodeFile(filename)))
                publishProgress(index)
            }
            catch (e: Exception) {
                this.exception = e
                Log.e(LOG_TAG, "Error while loading image $dirName: ${e.message}")
            }
        }
        if (movieBitmaps.isEmpty()) {
            movieBitmaps= mutableListOf(MovieBitmap(EMPTY_MOVIE, Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8)))
        }
        return null
    }

    override fun onProgressUpdate(vararg values: Int?) {
        super.onProgressUpdate(values[0])
        progressBar.progress = values[0]!!.toInt()
    }

    override fun onPostExecute(result: Void?) {
        super.onPostExecute(result)
        postTaskListener.onPostTask(ArrayList<MovieBitmap>(movieBitmaps), syncTaskType, exception)
        progressBar.visibility = View.GONE
    }
}