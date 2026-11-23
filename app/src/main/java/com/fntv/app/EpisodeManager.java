package com.fntv.app;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.fntv.app.api.FnApiManager;
import com.fntv.app.api.model.ApiResponse;
import com.fntv.app.api.model.PlayListItem;

import java.util.List;

import retrofit2.Call;
import retrofit2.Response;

/** 剧集列表管理 — 加载、切换剧集 */
public class EpisodeManager {

    public interface Callback {
        String getBaseUrl();
        String getParentGuid();
        String getItemGuid();
        int getEpisodeNumber();
        FnApiManager getApiManager();
        Context getContext();
        void onSwitchEpisode(String guid, String title);
    }

    private List<PlayListItem> episodeList;
    private int currentEpIndex = -1;
    private boolean loadingEpisodes = false;
    private boolean isCustomPlaylist = false;
    private String playlistTitle;
    private final Callback cb;
    private final Button btnEpisodeList;
    private final Button btnNextEp;
    private String currentGuid;
    private String currentTitle;

    private static final String TAG = "Player";

    public EpisodeManager(Callback cb, Button btnEpisodeList, Button btnNextEp) {
        this.cb = cb;
        this.btnEpisodeList = btnEpisodeList;
        this.btnNextEp = btnNextEp;
    }

    /** 设置自定义文件夹/文件列表播放列表 */
    public void setCustomPlaylist(List<PlayListItem> list, String title, String initialGuid) {
        this.episodeList = list != null ? new java.util.ArrayList<>(list) : null;
        this.isCustomPlaylist = true;
        this.playlistTitle = title;
        this.currentEpIndex = -1;
        if (episodeList != null) {
            for (int i = 0; i < episodeList.size(); i++) {
                PlayListItem item = episodeList.get(i);
                if (item != null && item.guid != null && item.guid.equals(initialGuid)) {
                    currentEpIndex = i;
                    break;
                }
            }
            if (currentEpIndex < 0 && !episodeList.isEmpty()) {
                currentEpIndex = 0;
            }
        }
        if (btnEpisodeList != null) {
            btnEpisodeList.setVisibility(View.VISIBLE);
        }
        updateBtnText();
        updateNextBtn();
    }

    public boolean isCustomPlaylist() {
        return isCustomPlaylist;
    }

    public String getPlaylistTitle() {
        return playlistTitle;
    }

    /** 是否有下一集/下一首 */
    public boolean hasNext() {
        if (episodeList == null || episodeList.isEmpty()) return false;
        if (isCustomPlaylist) return true; // 自定义文件夹播放列表支持列表循环连播
        return currentEpIndex >= 0 && currentEpIndex < episodeList.size() - 1;
    }

    /** 是否有上一集/上一首 */
    public boolean hasPrev() {
        if (episodeList == null || episodeList.isEmpty()) return false;
        if (isCustomPlaylist) return true; // 自定义文件夹播放列表支持列表循环连播
        return currentEpIndex > 0 && currentEpIndex < episodeList.size();
    }

    /** 是否正在加载 */
    public boolean isLoading() { return loadingEpisodes; }

    /** 是否已加载列表 */
    public boolean isLoaded() { return episodeList != null && !episodeList.isEmpty(); }

    /** 加载剧集列表 */
    public void loadList(String parentGuid) {
        if (isCustomPlaylist) return; // 自定义文件夹播放列表无需从接口拉取电视剧集
        if (parentGuid == null || parentGuid.isEmpty()) return;
        loadingEpisodes = true;
        Log.d(TAG, "getEpisodeList 请求: " + cb.getBaseUrl() + "/v/api/v1/episode/list/" + parentGuid);
        cb.getApiManager().getApi().getEpisodeList(parentGuid).enqueue(
                new retrofit2.Callback<ApiResponse<List<PlayListItem>>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<List<PlayListItem>>> call,
                                           Response<ApiResponse<List<PlayListItem>>> resp) {
                        loadingEpisodes = false;
                        Log.d(TAG, "getEpisodeList 响应 code=" + resp.code()
                                + " body=" + (resp.body() != null ? "code=" + resp.body().code + " size="
                                + (resp.body().data != null ? resp.body().data.size() : "null") : "null"));
                        if (resp.isSuccessful() && resp.body() != null && resp.body().code == 0
                                && resp.body().data != null && !resp.body().data.isEmpty()) {
                            episodeList = resp.body().data;
                            isCustomPlaylist = false;
                            currentEpIndex = -1;
                            int epNum = cb.getEpisodeNumber();
                            String itemGuid = cb.getItemGuid();
                            for (int i = 0; i < episodeList.size(); i++) {
                                PlayListItem ep = episodeList.get(i);
                                if (ep.guid.equals(itemGuid)) {
                                    currentEpIndex = i;
                                    break;
                                }
                                if (currentEpIndex < 0 && epNum > 0 && ep.episodeNumber == epNum) {
                                    currentEpIndex = i;
                                }
                            }
                            Log.d(TAG, "getEpisodeList 成功: " + episodeList.size() + " 集, currentIdx=" + currentEpIndex
                                    + " epNum=" + epNum + " itemGuid=" + itemGuid);
                            if (btnEpisodeList != null) btnEpisodeList.setVisibility(View.VISIBLE);
                            updateBtnText();
                            updateNextBtn();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<List<PlayListItem>>> call, Throwable t) {
                        loadingEpisodes = false;
                        Log.e(TAG, "getEpisodeList 失败: " + t.getMessage());
                    }
                });
    }

    /** 播放下一个项目（下一集/下一首MV） */
    public void playNext() {
        if (!hasNext()) return;
        int nextIndex = currentEpIndex + 1;
        if (nextIndex >= episodeList.size()) {
            if (isCustomPlaylist) {
                nextIndex = 0; // 文件夹循环连播
            } else {
                return;
            }
        }
        currentEpIndex = nextIndex;
        PlayListItem next = episodeList.get(currentEpIndex);
        currentGuid = next.guid;
        currentTitle = next.getDisplayTitle() != null ? next.getDisplayTitle() : next.title;
        updateBtnText();
        updateNextBtn();
        cb.onSwitchEpisode(currentGuid, currentTitle);
    }

    /** 播放上一个项目（方向盘/遥控上键） */
    public void playPrev() {
        if (!hasPrev()) return;
        int prevIndex = currentEpIndex - 1;
        if (prevIndex < 0) {
            if (isCustomPlaylist) {
                prevIndex = episodeList.size() - 1; // 文件夹循环连播
            } else {
                return;
            }
        }
        currentEpIndex = prevIndex;
        PlayListItem prev = episodeList.get(currentEpIndex);
        currentGuid = prev.guid;
        currentTitle = prev.getDisplayTitle() != null ? prev.getDisplayTitle() : prev.title;
        updateBtnText();
        updateNextBtn();
        cb.onSwitchEpisode(currentGuid, currentTitle);
    }

    /** 显示剧集/播放列表选择器 */
    public void showPicker() {
        if (episodeList == null || episodeList.isEmpty()) return;
        final String[] items = new String[episodeList.size()];
        for (int i = 0; i < episodeList.size(); i++) {
            PlayListItem ep = episodeList.get(i);
            String title = ep.getDisplayTitle() != null ? ep.getDisplayTitle() : (ep.title != null ? ep.title : "");
            String dur = ep.getFormattedDuration();
            if (isCustomPlaylist) {
                items[i] = (i + 1) + ". " + title + (dur.isEmpty() ? "" : "  (" + dur + ")");
            } else {
                items[i] = "EP" + (ep.episodeNumber > 0 ? ep.episodeNumber : (i + 1))
                        + "  " + title + (dur.isEmpty() ? "" : "  (" + dur + ")");
            }
        }
        String dlgTitle = isCustomPlaylist
                ? ((playlistTitle != null && !playlistTitle.isEmpty()) ? ("播放列表 · " + playlistTitle) : "播放列表")
                : "选择剧集";
        new android.app.AlertDialog.Builder(cb.getContext())
                .setTitle(dlgTitle)
                .setItems(items, (dialog, which) -> {
                    if (which >= 0 && which < episodeList.size()) {
                        PlayListItem s = episodeList.get(which);
                        currentEpIndex = which;
                        currentGuid = s.guid;
                        currentTitle = s.getDisplayTitle() != null ? s.getDisplayTitle() : s.title;
                        updateBtnText();
                        updateNextBtn();
                        cb.onSwitchEpisode(currentGuid, currentTitle);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 更新按钮文字 */
    private void updateBtnText() {
        if (btnEpisodeList != null) {
            if (isCustomPlaylist && episodeList != null) {
                btnEpisodeList.setText("列表 (" + (currentEpIndex + 1) + "/" + episodeList.size() + ")");
            } else {
                btnEpisodeList.setText("剧集");
            }
        }
        if (btnNextEp != null) {
            btnNextEp.setText(isCustomPlaylist ? "下一首" : "下一集");
        }
    }

    /** 重置状态（仅对非自定义列表重置；若是自定义列表则保留） */
    public void reset() {
        if (isCustomPlaylist) return;
        episodeList = null;
        currentEpIndex = -1;
        loadingEpisodes = false;
    }

    /** 强制清除所有播放列表 */
    public void clear() {
        isCustomPlaylist = false;
        playlistTitle = null;
        episodeList = null;
        currentEpIndex = -1;
        loadingEpisodes = false;
    }

    private void updateNextBtn() {
        if (btnNextEp != null) {
            btnNextEp.setVisibility(hasNext() ? View.VISIBLE : View.GONE);
        }
    }
}
