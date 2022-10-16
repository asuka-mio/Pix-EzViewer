/*
 * MIT License
 *
 * Copyright (c) 2020 ultranity
 * Copyright (c) 2019 Perol_Notsfsssf
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE
 */

package com.perol.asdpl.pixivez.fragments


import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Pair
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.perol.asdpl.pixivez.R
import com.perol.asdpl.pixivez.activity.BlockActivity
import com.perol.asdpl.pixivez.activity.UserFollowActivity
import com.perol.asdpl.pixivez.activity.UserMActivity
import com.perol.asdpl.pixivez.adapters.PictureXAdapter
import com.perol.asdpl.pixivez.databinding.FragmentPictureXBinding
import com.perol.asdpl.pixivez.dialog.CommentDialog
import com.perol.asdpl.pixivez.dialog.TagsBookMarkDialog
import com.perol.asdpl.pixivez.objects.AdapterRefreshEvent
import com.perol.asdpl.pixivez.objects.ThemeUtil
import com.perol.asdpl.pixivez.objects.Toasty
import com.perol.asdpl.pixivez.responses.Illust
import com.perol.asdpl.pixivez.services.GlideApp
import com.perol.asdpl.pixivez.services.PxEZApp
import com.perol.asdpl.pixivez.viewmodel.PictureXViewModel
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode


// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_ILLUSTID = "param1"
private const val ARG_ILLUSTOBJ = "param2"

/**
 * A simple [Fragment] subclass for pic detail.
 * Use the [PictureXFragment.newInstance] factory method to
 * create an instance of this fragment.
 *
 */
class PictureXFragment : BaseFragment() {

    private var param1: Long? = null
    private var param2: Illust? = null
    private lateinit var pictureXViewModel: PictureXViewModel
    override fun loadData() {
//        val item = activity?.intent?.extras
//        val illust = item?.getParcelable<Illust>(param1.toString())
        if (param2 != null)
            pictureXViewModel.firstGet(param2!!)
        else
            pictureXViewModel.firstGet(param1!!)
    }

    override fun onDestroy() {
        pictureXAdapter?.setListener { }
        pictureXAdapter?.setViewCommentListen { }
        pictureXAdapter?.setBookmarkedUserListen { }
        pictureXAdapter?.setUserPicLongClick { }
        super.onDestroy()

    }

    override fun onResume() {
        super.onResume()
        pictureXAdapter?.imageViewGif?.startPlay()
    }

    override fun onPause() {
        pictureXAdapter?.imageViewGif?.pausePlay()
        super.onPause()

    }

    private var pictureXAdapter: PictureXAdapter? = null

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event: AdapterRefreshEvent) {
        lifecycleScope.launch {
            val allTags = blockViewModel.getAllTags()
            blockTags = allTags.map {
                it.name
            }
            var needBlock = false
            pictureXViewModel.illustDetail.value?.tags?.forEach {
                if (blockTags.contains(it.name)) needBlock = true
            }
            if (!needBlock) {
                binding.blockView.visibility = View.GONE
            }
        }
    }

    private fun initViewModel() {
        pictureXViewModel.illustDetail.observe(viewLifecycleOwner) { it ->
            binding.progressView.visibility = View.GONE
            if (it != null) {
                val tags = it.tags.map { rt ->
                    rt.name
                }
                for (i in tags) {
                    if (blockTags.contains(i)) {
                        binding.jumpButton.setOnClickListener {
                            startActivity(Intent(requireActivity(), BlockActivity::class.java))
                        }
                        binding.blocktagTextview.text = i
                        binding.blockView.visibility = View.VISIBLE
                    }
                }
                binding.illust = it

                position = if (it.meta_pages.isNotEmpty())
                    it.meta_pages.size
                else 1
                pictureXAdapter =
                    PictureXAdapter(pictureXViewModel, it, requireContext()).also {
                        it.setListener {
                            //                        activity?.supportStartPostponedEnterTransition()
                            if (!hasMoved) {
                                binding.recyclerview.scrollToPosition(0)
                                (binding.recyclerview.layoutManager as LinearLayoutManager?)?.scrollToPositionWithOffset(
                                    0,
                                    0
                                )
                            }
                            pictureXViewModel.getRelative(param1!!)

                        }
                        it.setViewCommentListen {
                            val commentDialog =
                                CommentDialog.newInstance(pictureXViewModel.illustDetail.value!!.id)
                            commentDialog.show(childFragmentManager)
                        }
                        it.setBookmarkedUserListen  {
                            val intent = Intent(requireActivity().applicationContext, UserFollowActivity::class.java)
                            val bundle = Bundle()
                            bundle.putLong("illust_id", pictureXViewModel.illustDetail.value!!.id)
                            intent.putExtras(bundle)
                            startActivity(intent)
                        }
                        it.setUserPicLongClick {
                            pictureXViewModel.likeUser()
                        }
                    }

                binding.recyclerview.adapter = pictureXAdapter
                if (it.user.is_followed)
                    binding.imageViewUserPicX.setBorderColor(Color.YELLOW)
                //else
                //    binding.imageViewUserPicX.setBorderColor(ContextCompat.getColor(requireContext(), colorPrimary))
                binding.imageViewUserPicX.setOnLongClickListener {
                    pictureXViewModel.likeUser()
                    true
                }
                binding.imageViewUserPicX.setOnClickListener { _ ->
                    val intent = Intent(context, UserMActivity::class.java)
                    intent.putExtra("data", it.user.id)

                    if (PxEZApp.animationEnable) {
                        val options = ActivityOptions.makeSceneTransitionAnimation(
                            context as Activity,
                            Pair.create(binding.imageViewUserPicX, "UserImage")
                        )
                        startActivity(intent, options.toBundle())
                    } else
                        startActivity(intent)
                }
                binding.fab.show()
            } else {
                if (parentFragmentManager.backStackEntryCount <= 1)
                    activity?.finish()
                else
                    parentFragmentManager.popBackStack()
            }
        }
        pictureXViewModel.relatedPics.observe(viewLifecycleOwner) {
            pictureXAdapter?.setRelatedPics(it)
        }
        pictureXViewModel.likeIllust.observe(viewLifecycleOwner) {
            if (it != null) {
                if (it) {
                    GlideApp.with(this).load(R.drawable.heart_red).into(binding.fab)
                } else {
                    GlideApp.with(this).load(R.drawable.ic_action_heart).into(binding.fab)
                }

            }
        }
        pictureXViewModel.followUser.observe(viewLifecycleOwner) {
            binding.imageViewUserPicX.setBorderColor(
                if (it) Color.YELLOW else ThemeUtil.getColor(
                    requireContext(),
                    androidx.appcompat.R.attr.colorPrimary
                )
            )
            pictureXAdapter?.setUserPicColor(it)
        }
        pictureXViewModel.progress.observe(viewLifecycleOwner) {
            pictureXAdapter?.setProgress(it)
        }
        pictureXViewModel.downloadGifSuccess.observe(viewLifecycleOwner) {

            pictureXAdapter?.setProgressComplete(it)
        }
        pictureXAdapter?.notifyDataSetChanged()
    }

    var hasMoved = false
    private fun initView() {
        binding.fab.setOnClickListener {
            pictureXViewModel.fabClick()
        }
        binding.fab.setOnLongClickListener {
            if (pictureXViewModel.illustDetail.value!!.is_bookmarked) {
                return@setOnLongClickListener true
            }
            Toasty.info(
                requireActivity(),
                resources.getString(R.string.fetchtags),
                Toast.LENGTH_SHORT
            ).show()
            val tagsBookMarkDialog = TagsBookMarkDialog()
            tagsBookMarkDialog.show(childFragmentManager, TagsBookMarkDialog::class.java.name)
            true
        }
        binding.imageButton.setOnClickListener {
            binding.recyclerview.scrollToPosition(position)
            binding.constraintLayoutFold.visibility = View.INVISIBLE
        }
        val linearLayoutManager = LinearLayoutManager(requireActivity())
        binding.recyclerview.layoutManager = linearLayoutManager
        binding.recyclerview.setHasFixedSize(true)
        binding.recyclerview.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                hasMoved = true
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                (binding.recyclerview.layoutManager as LinearLayoutManager).run {
                    /*Log.d("test", "onScrolled: "+
                            findFirstCompletelyVisibleItemPosition().toString()+" "+
                            findLastCompletelyVisibleItemPosition().toString()+" "+
                            findFirstVisibleItemPosition().toString() +" "+
                            findLastVisibleItemPosition().toString()+" "+position)*/
                    if (findFirstVisibleItemPosition() <= position && findLastVisibleItemPosition() >= position -1) {
                        binding.constraintLayoutFold.visibility = View.INVISIBLE
                    } else if (findFirstVisibleItemPosition() > position || findLastVisibleItemPosition() < position ) {
                        binding.constraintLayoutFold.visibility = View.VISIBLE
                    }
                }

                }
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getLong(ARG_ILLUSTID)
            param2 = it.getParcelable(ARG_ILLUSTOBJ)
        }
        pictureXViewModel = ViewModelProvider(this)[PictureXViewModel::class.java]
    }

    lateinit var binding: FragmentPictureXBinding
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        if(! this::binding.isInitialized) {
            binding = FragmentPictureXBinding.inflate(inflater, container, false).apply {
                lifecycleOwner = this@PictureXFragment
            }
        }
        position = param2?.meta_pages?.size?: 1
        return binding.root
    }

    var position = 0


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViewModel()
        initView()
    }


    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment PictureXFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: Long?,param2: Illust?) =
            PictureXFragment().apply {
                arguments = Bundle().apply {
                    if (param2 != null) {
                        putParcelable(ARG_ILLUSTOBJ, param2)
                        putLong(ARG_ILLUSTID, param2.id)
                    } else {
                        putLong(ARG_ILLUSTID, param1!!)
                    }
/*                    putParcelable("illust", illust)*/
                }
            }
    }
}
