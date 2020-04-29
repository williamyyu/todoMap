package com.todomap.map

import android.Manifest
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.SnapHelper
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.snackbar.Snackbar
import com.todomap.R
import com.todomap.database.TodoDatabase
import com.todomap.databinding.FragmentMapBinding
import permissions.dispatcher.NeedsPermission
import permissions.dispatcher.OnPermissionDenied
import permissions.dispatcher.RuntimePermissions


@RuntimePermissions
class MapFragment : Fragment(), OnMapReadyCallback {

    private lateinit var googleMap: GoogleMap

    private lateinit var viewModel: MapViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val binding: FragmentMapBinding =
            DataBindingUtil.inflate(inflater, R.layout.fragment_map, container, false)

        val application = requireNotNull(this.activity).application
        val databaseDao = TodoDatabase.getInstance(application).todoDatabaseDao
        viewModel = MapViewModelFactory(databaseDao, application).create(MapViewModel::class.java)

        binding.viewModel = viewModel
        binding.lifecycleOwner = this

        viewModel.isMapReady.observe(viewLifecycleOwner, Observer { isMapReady ->
            if (isMapReady == true) {
                requestLastLocationWithPermissionCheck()

                googleMap.setOnMyLocationButtonClickListener {
                    requestLastLocationWithPermissionCheck()
                    true
                }
            }
        })

        viewModel.location.observe(viewLifecycleOwner, Observer {
            val cameraPosition = CameraPosition.Builder()
                .target(LatLng(it.latitude, it.longitude))
                .zoom(17f)
                .build()
            googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
        })

        viewModel.snackbarEvent.observe(viewLifecycleOwner, Observer {
            Snackbar.make(
                activity!!.findViewById(android.R.id.content),
                it,
                Snackbar.LENGTH_LONG
            ).show()
        })

        val adapter = TodoAdapter()
        binding.recyclerView.adapter = adapter

        val snapHelper: SnapHelper = LinearSnapHelper()
        snapHelper.attachToRecyclerView(binding.recyclerView)

        viewModel.allTodoList.observe(viewLifecycleOwner, Observer {
            adapter.submitList(it)
        })

        binding.fabCreateTodo.setOnClickListener {
            viewModel.onFabClicked()

            if (binding.recyclerView.translationY != 0f) {
                binding.recyclerView.animate().translationY(0f).start()
            } else {
                binding.recyclerView.animate()
                    .translationY(-binding.recyclerView.height.toFloat())
                    .start()
            }
        }

        val googleMapFragment =
            childFragmentManager.findFragmentById(R.id.googleMapFragment) as SupportMapFragment
        googleMapFragment.getMapAsync(this)

        return binding.root
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        onRequestPermissionsResult(requestCode, grantResults)
    }

    override fun onMapReady(map: GoogleMap) {
        this.googleMap = map.apply {
            uiSettings.isZoomControlsEnabled = true

            setOnMapClickListener {
                val marker = googleMap.addMarker(
                    MarkerOptions()
                        .position(it)
                        .title("Chosen location")
                )
                googleMap.animateCamera(CameraUpdateFactory.newLatLng(it))

                viewModel.onMarkerAdded(it, marker)
            }

            setOnMarkerClickListener {
                true
            }
        }

        viewModel.onMapReady()
    }

    @NeedsPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun requestLastLocation() {
        googleMap.isMyLocationEnabled = true
        viewModel.onRequestLastLocation()
    }

    @OnPermissionDenied(Manifest.permission.ACCESS_FINE_LOCATION)
    fun onPermissionDenied() {
        viewModel.onPermissionDenied()
    }
}
