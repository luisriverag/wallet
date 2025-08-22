package com.mycelium.wallet.activity.modern.adapter

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayout
import com.mycelium.wallet.MbwManager

class TabsAdapter(val activity: AppCompatActivity, val mbwManager: MbwManager) :
    FragmentStateAdapter(activity) {
    private val mTabs = mutableListOf<TabInfo>()

    private data class TabInfo(
        val clss: Class<*>,
        val args: Bundle?,
        val title: String,
        val tag: String
    )


    fun addTab(tab: TabLayout.Tab, clss: Class<*>, args: Bundle?, tabTag: String) {
        val info = TabInfo(clss, args, tab.text.toString(), tabTag)
        tab.tag = info
        mTabs.add(info)
        notifyDataSetChanged()
    }

    fun addTab(i: Int, tab: TabLayout.Tab, clss: Class<*>, args: Bundle, tabTag: String) {
        val info = TabInfo(clss, args, tab.text.toString(), tabTag)
        tab.tag = info
        mTabs.add(i, info)
        notifyDataSetChanged()
    }

    fun removeTab(tabTag: String) {
        mTabs.removeAll { it.tag == tabTag }
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = mTabs.size

    override fun createFragment(position: Int): Fragment =
        mTabs[position].let { Fragment.instantiate(activity, it.clss.name, it.args) }

    override fun getItemId(position: Int): Long =
        mTabs[position].tag.hashCode().toLong()

    fun getPageTitle(position: Int): CharSequence? =
        mTabs[position].title

    fun getPageTag(position: Int): String =
        mTabs[position].tag

    fun indexOf(tabTag: String): Int =
        mTabs.indexOfFirst { it.tag == tabTag }

//    override fun getItemPosition(`object`: Any): Int =
//        POSITION_NONE
}