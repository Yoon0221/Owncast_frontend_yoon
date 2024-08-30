package kr.dori.android.own_cast.playlist

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.dori.android.own_cast.ActivityMover
import kr.dori.android.own_cast.editAudio.EditAudioActivity
import kr.dori.android.own_cast.MainActivity
import kr.dori.android.own_cast.R
import kr.dori.android.own_cast.data.CastPlayerData
import kr.dori.android.own_cast.player.PlayCastActivity
import kr.dori.android.own_cast.databinding.FragmentCastBinding
import kr.dori.android.own_cast.forApiData.AuthResponse
import kr.dori.android.own_cast.forApiData.Cast
import kr.dori.android.own_cast.forApiData.GetPlayList
import kr.dori.android.own_cast.forApiData.Playlist
import kr.dori.android.own_cast.forApiData.getRetrofit
import kr.dori.android.own_cast.keyword.KeywordLoadingDialog
import kr.dori.android.own_cast.player.CastWithPlaylistId


class CastFragment() : Fragment(), ActivityMover {
    private lateinit var binding: FragmentCastBinding
    private lateinit var castAdapter: CastAdapter
    private lateinit var activityResultLauncher: ActivityResultLauncher<Intent>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentCastBinding.inflate(inflater, container, false)

        castAdapter = CastAdapter(this)


        // API 호출 및 데이터 설정
        // API 호출 및 데이터 설정

        // RecyclerView 설정
        binding.fragmentCastRv.adapter = castAdapter
        binding.fragmentCastRv.layoutManager = LinearLayoutManager(context)
        loadCastInfo()

        // Back 버튼 클릭 이벤트 처리
        binding.fragmentCastBackIv.setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }
        // Initialize ActivityResultLauncher
        activityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                Log.d("ifsuccess", "success")
                val data: Intent? = result.data
                val isSuccess = data?.getBooleanExtra("result", false) ?: false
                (activity as? MainActivity)?.setPlaylistTableVisibility(isSuccess)
            }
        }



        return binding.root
    }


    override fun ToPlayCast() {

        //   val currentCast = CastPlayerData.currentCast


        val intent = Intent(requireContext(), PlayCastActivity::class.java)

        activityResultLauncher.launch(intent)

    }



    override fun ToEditAudio(id: Long,playlistId:Long) {

        val intent = Intent(requireContext(), EditAudioActivity::class.java)
        intent.putExtra("id",id)
        intent.putExtra("playlistId",playlistId)
        startActivity(intent)
    }

    private fun parseTimeToSeconds(input: String): Int {
        return if (input.contains(":")) {
            val parts = input.split(":")
            val minutes = parts[0].toIntOrNull() ?: 0
            val seconds = parts[1].toIntOrNull() ?: 0
            (minutes * 60) + seconds
        } else {
            input.toIntOrNull() ?: 0
        }
    }

    private fun getTotalAudioLengthInSeconds(castList: List<CastWithPlaylistId>): Int {
        return castList.sumOf { cast ->
            parseTimeToSeconds(cast.audioLength)
        }
    }

    private fun formatTime(input: Int): String {
        val minutes = input / 60
        val seconds = input % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun loadCastInfo(){
        val loadingdialog = KeywordLoadingDialog(requireContext(),"목록을 받아오는 중이에요")
        loadingdialog.setCancelable(false)
        loadingdialog.setCanceledOnTouchOutside(false)
        loadingdialog.show()
        val isSave = arguments?.getBoolean("isSave") ?: false

        val getPlaylist = getRetrofit().create(Playlist::class.java)

        CoroutineScope(Dispatchers.Main).launch {
            try {
                if (!isSave) {
                    val response = getPlaylist.getSaved()
                    Log.d("CastFragment", "getSaved API 호출")
                    if (response.isSuccessful) {
                        Log.d("CastFragment", "getSaved API 성공")
                        val playlistInfo = response.body()?.result
                        withContext(Dispatchers.Main) {
                            playlistInfo?.let {
                                val castListWithPlaylistId = it.castList.map { cast ->
                                    CastWithPlaylistId(
                                        castId = cast.castId,
                                        playlistId = cast.playlistId,
                                        castTitle = cast.castTitle?:"untitled",
                                        isPublic = cast.isPublic,
                                        castCreator = cast.castCreator,
                                        castCategory = cast.castCategory,
                                        audioLength = cast.audioLength,
                                        imagePath = cast.imagePath
                                    )
                                }
                                castAdapter.dataList = castListWithPlaylistId.toMutableList()

                                if(!castAdapter.dataList.isNullOrEmpty()){
                                    binding.fragmentCastPlayIv.setOnClickListener {
                                        CastPlayerData.setCast(castAdapter.dataList, 0)
                                        ToPlayCast()
                                    }

                                    binding.fragmentCastShuffleIv.setOnClickListener {
                                        val forShuffle: List<CastWithPlaylistId> = castAdapter.dataList
                                        val shuffledList = forShuffle.shuffled()
                                        CastPlayerData.setCast(shuffledList,0)
                                        ToPlayCast()
                                    }
                                }else{
                                    binding.fragmentCastPlayIv.isEnabled = false
                                    binding.fragmentCastShuffleIv.isEnabled = false
                                }
                                // 총 오디오 길이 계산
                                val totalAudioLengthInSeconds = getTotalAudioLengthInSeconds(castListWithPlaylistId)
                                binding.castInfo.text = "${castListWithPlaylistId.size}개, ${formatTime(totalAudioLengthInSeconds)}"
                                Log.d("realTest","${castListWithPlaylistId.toMutableList()}")
                                Glide.with(requireActivity()).load(R.drawable.others).into(binding.fragmentCastTitleIv)
                            }
                        }
                    } else {
                        Log.e("CastFragment", "getSaved API 실패: ${response.errorBody()?.string()}")
                    }
                } else {
                    val response = getPlaylist.getMy()
                    Log.d("CastFragment", "getMy API 호출")
                    if (response.isSuccessful) {
                        Log.d("CastFragment", "getMy API 성공")
                        val playlistInfo = response.body()?.result
                        withContext(Dispatchers.Main) {
                            playlistInfo?.let {
                                val castListWithPlaylistId = it.castList.map { cast ->
                                    CastWithPlaylistId(
                                        castId = cast.castId,
                                        playlistId = cast.playlistId,
                                        castTitle = cast.castTitle?:"untitled",
                                        isPublic = cast.isPublic,
                                        castCreator = cast.castCreator,
                                        castCategory = cast.castCategory,
                                        audioLength = cast.audioLength,
                                        imagePath = cast.imagePath
                                    )
                                }
                                castAdapter.dataList = castListWithPlaylistId.toMutableList()
                                if(!castAdapter.dataList.isNullOrEmpty()){
                                    binding.fragmentCastPlayIv.setOnClickListener {
                                        CastPlayerData.setCast(castAdapter.dataList, 0)
                                        ToPlayCast()
                                    }

                                    binding.fragmentCastShuffleIv.setOnClickListener {
                                        val forShuffle: List<CastWithPlaylistId> = castAdapter.dataList
                                        val shuffledList = forShuffle.shuffled()
                                        CastPlayerData.setCast(shuffledList,0)
                                        ToPlayCast()
                                    }
                                }else{
                                    binding.fragmentCastPlayIv.isEnabled = false
                                    binding.fragmentCastShuffleIv.isEnabled = false
                                }
                                // 총 오디오 길이 계산
                                val totalAudioLengthInSeconds = getTotalAudioLengthInSeconds(castListWithPlaylistId)
                                binding.castInfo.text = "${castListWithPlaylistId.size}개, ${formatTime(totalAudioLengthInSeconds)}"
                                Glide.with(requireActivity()).load(R.drawable.mines).into(binding.fragmentCastTitleIv)
                            }
                        }
                    } else {
                        Log.e("CastFragment", "getMy API 실패: ${response.errorBody()?.string()}")
                    }
                }
                binding.fragmentCastRv.adapter = castAdapter
                binding.fragmentCastRv.layoutManager = LinearLayoutManager(context)
            } catch (e: Exception) {
                Log.e("CastFragment", "API 호출 중 예외 발생", e)
                e.printStackTrace()
            } finally {
                withContext(Dispatchers.Main) {
                    loadingdialog.dismiss()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadCastInfo()
    }
}
