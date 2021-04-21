package com.example.czolgi.choseTank

import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import com.example.czolgi.MainActivity
import com.example.czolgi.R
import com.example.czolgi.databinding.FragmentChoseTankBinding

class ChoseTankFragment : Fragment() {

    companion object {
        fun newInstance() = ChoseTankFragment()
    }

    private lateinit var viewModel: ChoseTankViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding: FragmentChoseTankBinding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_chose_tank,
            container,
            false
        )
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProvider(this).get(ChoseTankViewModel::class.java)
        // TODO: Use the ViewModel
    }

}