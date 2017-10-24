package com.example.meeera.cutsong.Activity

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.design.widget.TabLayout
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import android.support.v4.app.FragmentStatePagerAdapter
import android.support.v4.view.ViewPager
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.example.meeera.cutsong.Fragment.Music
import com.example.meeera.cutsong.Fragment.Recorder
import com.example.meeera.cutsong.R
import java.util.ArrayList

class MainActivity : AppCompatActivity() {

    lateinit var viewpager : ViewPager
    lateinit var tabLayout : TabLayout
    var viewpagerAdapter : viewPagerAdapter = viewPagerAdapter(supportFragmentManager)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        viewpager = findViewById(R.id.viewpager) as ViewPager
        tabLayout = findViewById(R.id.tabLayout) as TabLayout
    }

    override fun onResume() {
        super.onResume()
        setUpViewPager(viewpager)
        tabLayout.setupWithViewPager(viewpager)
        setUpTabIcons()
    }

    fun setUpViewPager(viewPager: ViewPager) {
        viewpagerAdapter.addFrag(Music())
        viewpagerAdapter.addFrag(Recorder())
        viewPager.adapter = viewpagerAdapter
    }

    fun setUpTabIcons() {
        if (tabLayout != null) {
            tabLayout.setupWithViewPager(viewpager)

            for (i in 0..tabLayout.tabCount - 1) {
                val tab = tabLayout.getTabAt(i)
                tab?.setCustomView(viewpagerAdapter.getTabView(i))
            }
            tabLayout.getTabAt(1)!!.customView!!.isSelected = true
        }
    }

    class viewPagerAdapter(fm : FragmentManager) : FragmentStatePagerAdapter(fm) {

        var fragmnt : Fragment ?= null
        private val mFragmentList = ArrayList<Fragment>()
        private val mFragmentListtitle = ArrayList<String>()
        private val mTabsTitle = arrayOf("Music", "Recoder")
        private val mTabsIcons = intArrayOf(R.drawable.ic_music, R.drawable.ic_recorder)

        override fun getItem(position: Int): Fragment {
            return mFragmentList[position]
        }

        override fun getCount(): Int {
            return mFragmentList.size
        }

        fun getTabView(position: Int) : View {
            var view  : View = LayoutInflater.from(fragmnt?.context).inflate(R.layout.custom_tab_text, null)
            val title = view.findViewById<TextView>(R.id.titletab)
            title.text = mTabsTitle[position]
            val icon = view.findViewById<ImageView>(R.id.icon)
            icon.setImageResource(mTabsIcons[position])
            return view
        }

        fun addFrag(fragment: Fragment) {
            fragmnt = fragment
            mFragmentList.add(fragment)
        }
    }
}
