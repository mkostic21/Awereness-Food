/*
 * Copyright (c) 2021 Razeware LLC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * Notwithstanding the foregoing, you may not use, copy, modify, merge, publish,
 * distribute, sublicense, create a derivative work, and/or sell copies of the
 * Software in any work that is designed, intended, or marketed for pedagogical or
 * instructional purposes related to programming, coding, application development,
 * or information technology.  Permission for such use, copying, modification,
 * merger, publication, distribution, sublicensing, creation of derivative works,
 * or sale is expressly withheld.
 *
 * This project and source code may use libraries or frameworks that are
 * released under various Open-Source licenses. Use of those libraries and
 * frameworks are governed by their own individual licenses.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.raywenderlich.android.awareness_food.monitor

import android.content.Context
import android.content.Context.CONNECTIVITY_SERVICE
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.net.NetworkRequest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class NetworkMonitor @Inject constructor(
    private val context: Context,
    private val lifecycle: Lifecycle
) : LifecycleObserver {

    private lateinit var networkCallback: ConnectivityManager.NetworkCallback
    private var connectivityManager: ConnectivityManager? = null
    private val validNetworks = HashSet<Network>()

    private lateinit var job: Job
    private lateinit var coroutineScope: CoroutineScope

    private val _networkAvailableStateFlow: MutableStateFlow<NetworkState> =
        MutableStateFlow(NetworkState.Available)
    val networkAvailableStateFlow
        get() = _networkAvailableStateFlow

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun init() {
        connectivityManager =
            context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun registerNetworkCallback() {
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            initCoroutine()
            initNetworkMonitoring()
            checkCurrentNetworkState()
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun unregisterNetworkCallback() {
        validNetworks.clear()
        connectivityManager?.unregisterNetworkCallback(networkCallback)
        job.cancel()
    }

    private fun initCoroutine() {
        job = Job()
        coroutineScope = CoroutineScope(Dispatchers.Default + job)
    }

    private fun initNetworkMonitoring() {
        networkCallback = createNetworkCallback()

        val networkRequest = NetworkRequest.Builder()
            .addCapability(NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager?.registerNetworkCallback(networkRequest, networkCallback)
    }

    private fun createNetworkCallback() = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            connectivityManager?.getNetworkCapabilities(network).also {
                if (it?.hasCapability(NET_CAPABILITY_INTERNET) == true) {
                    validNetworks.add(network)
                }
            }
            checkValidNetworks()
        }

        override fun onLost(network: Network) {
            validNetworks.remove(network)
            checkValidNetworks()
        }
    }

    private fun checkCurrentNetworkState() {
        connectivityManager?.allNetworks?.let {
            validNetworks.addAll(it)
        }
        checkValidNetworks()
    }

    private fun checkValidNetworks() {
        coroutineScope.launch {
            _networkAvailableStateFlow.emit(
                if (validNetworks.size > 0)
                    NetworkState.Available
                else
                    NetworkState.Unavailable
            )
        }
    }
}

sealed class NetworkState {
    object Unavailable : NetworkState()
    object Available : NetworkState()
}