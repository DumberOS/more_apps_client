package com.dumbermini.apps.ui.appList

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import com.google.android.material.imageview.ShapeableImageView
import com.dumbermini.apps.R
import com.dumbermini.apps.database.Database
import com.dumbermini.apps.model.ProductItem
import com.dumbermini.apps.model.Repository
import com.dumbermini.apps.utility.common.extension.authentication
import com.dumbermini.apps.utility.common.extension.corneredBackground
import com.dumbermini.apps.utility.common.extension.dp
import com.dumbermini.apps.utility.common.extension.getColorFromAttr
import com.dumbermini.apps.utility.common.extension.inflate
import com.dumbermini.apps.utility.common.extension.setTextSizeScaled
import com.dumbermini.apps.utility.common.log
import com.dumbermini.apps.utility.common.nullIfEmpty
import com.dumbermini.apps.utility.extension.resources.TypefaceExtra
import com.dumbermini.apps.widget.CursorRecyclerAdapter
import kotlin.system.measureTimeMillis
import com.google.android.material.R as MaterialR

class AppListAdapter(
    private val source: AppListFragment.Source,
    private val onClick: (packageName: String) -> Unit,
) : CursorRecyclerAdapter<AppListAdapter.ViewType, RecyclerView.ViewHolder>() {

    enum class ViewType { PRODUCT, LOADING, EMPTY }

    private inner class ProductViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name = itemView.findViewById<TextView>(R.id.name)!!
        val status = itemView.findViewById<TextView>(R.id.status)!!
        val summary = itemView.findViewById<TextView>(R.id.summary)!!
        val icon = itemView.findViewById<ShapeableImageView>(R.id.icon)!!

        init {
            itemView.setOnClickListener {
                log(measureTimeMillis { onClick(getPackageName(absoluteAdapterPosition)) }, "Bench")
            }
        }
    }

    private class LoadingViewHolder(context: Context) :
        RecyclerView.ViewHolder(FrameLayout(context)) {
        init {
            with(itemView as FrameLayout) {
                addView(
                    ProgressBar(context),
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { gravity = Gravity.CENTER }
                )
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.MATCH_PARENT
                )
            }
        }
    }

    private class EmptyViewHolder(context: Context) :
        RecyclerView.ViewHolder(TextView(context)) {
        val text: TextView
            get() = itemView as TextView

        init {
            with(itemView as TextView) {
                gravity = Gravity.CENTER
                setPadding(20.dp, 20.dp, 20.dp, 20.dp)
                typeface = TypefaceExtra.light
                setTextColor(context.getColorFromAttr(android.R.attr.colorPrimary))
                setTextSizeScaled(20)
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.MATCH_PARENT
                )
            }
        }
    }

    var repositories: Map<Long, Repository> = emptyMap()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    var emptyText: String = ""
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            if (field != value) {
                field = value
                if (isEmpty) {
                    notifyDataSetChanged()
                }
            }
        }

    override val viewTypeClass: Class<ViewType>
        get() = ViewType::class.java

    private val isEmpty: Boolean
        get() = super.getItemCount() == 0

    override fun getItemCount(): Int = if (isEmpty) 1 else super.getItemCount()

    override fun getItemId(position: Int): Long = when {
        isEmpty -> -1
        else -> super.getItemId(position)
    }

    override fun getItemEnumViewType(position: Int): ViewType {
        return when {
            !isEmpty -> ViewType.PRODUCT
            cursor == null -> ViewType.LOADING
            else -> ViewType.EMPTY
        }
    }

    private fun getPackageName(position: Int): String {
        return Database.ProductAdapter.transformPackageName(
            moveTo(position.coerceAtLeast(0))
        )
    }

    private fun getProductItem(position: Int): ProductItem {
        return Database.ProductAdapter.transformItem(
            moveTo(position.coerceAtLeast(0))
        )
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: ViewType,
    ): RecyclerView.ViewHolder {
        return when (viewType) {
            ViewType.PRODUCT -> ProductViewHolder(parent.inflate(R.layout.product_item))
            ViewType.LOADING -> LoadingViewHolder(parent.context)
            ViewType.EMPTY -> EmptyViewHolder(parent.context)
        }
    }

    private var updateBackground: ColorStateList? = null
    private var updateForeground: ColorStateList? = null
    private var installedBackground: ColorStateList? = null
    private var installedForeground: ColorStateList? = null
    private var defaultForeground: ColorStateList? = null

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (getItemEnumViewType(position)) {
            ViewType.PRODUCT -> {
                holder as ProductViewHolder
                val productItem = getProductItem(position)
                holder.name.text = productItem.name
                holder.summary.text = productItem.summary
                holder.summary.isVisible = productItem.summary.isNotEmpty()
                        && productItem.name != productItem.summary
                val repository = repositories[productItem.repositoryId]
                if (repository != null) {
                    val iconUrl = productItem.icon(view = holder.icon, repository = repository)
                    holder.icon.load(iconUrl) {
                        authentication(repository.authentication)
                    }
                }
                with(holder.status) {
                    val versionText = if (source == AppListFragment.Source.UPDATES) {
                        productItem.version
                    } else {
                        productItem.installedVersion.nullIfEmpty() ?: productItem.version
                    }
                    text = versionText
                    val isInstalled = productItem.installedVersion.nullIfEmpty() != null
                    when {
                        productItem.canUpdate -> {
                            if (updateBackground == null) {
                                updateBackground =
                                    context.getColorFromAttr(MaterialR.attr.colorTertiaryContainer)
                            }
                            if (updateForeground == null) {
                                updateForeground =
                                    context.getColorFromAttr(MaterialR.attr.colorOnTertiaryContainer)
                            }
                            backgroundTintList = updateBackground
                            setTextColor(updateForeground)
                        }

                        isInstalled -> {
                            if (installedBackground == null) {
                                installedBackground =
                                    context.getColorFromAttr(MaterialR.attr.colorSecondaryContainer)
                            }
                            if (installedForeground == null) {
                                installedForeground =
                                    context.getColorFromAttr(MaterialR.attr.colorOnSecondaryContainer)
                            }
                            backgroundTintList = installedBackground
                            setTextColor(installedForeground)
                        }

                        else -> {
                            setPadding(0, 0, 0, 0)
                            if (defaultForeground == null) {
                                defaultForeground =
                                    context.getColorFromAttr(MaterialR.attr.colorOnBackground)
                            }
                            setTextColor(defaultForeground)
                            background = null
                            return@with
                        }
                    }
                    background = context.corneredBackground
                    6.dp.let { setPadding(it, it, it, it) }
                }
                val enabled = productItem.compatible || productItem.installedVersion.isNotEmpty()
                holder.name.isEnabled = enabled
                holder.status.isEnabled = enabled
                holder.summary.isEnabled = enabled
            }

            ViewType.LOADING -> {
                // Do nothing
            }

            ViewType.EMPTY -> {
                holder as EmptyViewHolder
                holder.text.text = emptyText
            }
        }
    }
}
