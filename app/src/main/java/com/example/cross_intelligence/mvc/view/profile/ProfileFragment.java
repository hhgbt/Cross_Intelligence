package com.example.cross_intelligence.mvc.view.profile;

import android.content.Intent;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;

import com.example.cross_intelligence.R;
import com.example.cross_intelligence.databinding.FragmentProfileBinding;
import com.example.cross_intelligence.mvc.base.BaseFragment;
import com.example.cross_intelligence.mvc.controller.UserManager;
import com.example.cross_intelligence.mvc.model.User;
import com.example.cross_intelligence.mvc.sync.MockSyncApi;
import com.example.cross_intelligence.mvc.sync.NetworkMonitor;
import com.example.cross_intelligence.mvc.sync.SyncManager;
import com.example.cross_intelligence.mvc.sync.SyncState;
import com.example.cross_intelligence.mvc.util.PreferenceUtil;
import com.example.cross_intelligence.mvc.util.UIUtil;

/**
 * 个人信息展示 Fragment，读取本地 Realm 数据并支持跳转到设置页。
 */
public class ProfileFragment extends BaseFragment {

    private FragmentProfileBinding binding;
    private final UserManager userManager = new UserManager();
    private SyncManager syncManager;
    private NetworkMonitor networkMonitor;

    @Override
    protected int getLayoutId() {
        return R.layout.fragment_profile;
    }

    @Override
    protected void initView(@NonNull View root) {
        binding = FragmentProfileBinding.bind(root);
        binding.btnEdit.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), UserSettingsActivity.class);
            startActivity(intent);
        });
        binding.btnSync.setOnClickListener(v -> {
            if (syncManager != null) {
                syncManager.syncPendingCheckIns();
            }
        });
    }

    @Override
    protected void loadDataOnce() {
        fetchUserInfo();
    }

    @Override
    public void onResume() {
        super.onResume();
        fetchUserInfo();
    }

    private void fetchUserInfo() {
        if (getContext() == null) {
            return;
        }
        String currentUserId = PreferenceUtil.getString(requireContext(), "account", "");
        if (TextUtils.isEmpty(currentUserId)) {
            UIUtil.showToast(requireContext(), "请先登录");
            return;
        }
        userManager.fetchUser(currentUserId, this::renderUser);
        ensureSyncComponents();
    }

    private void ensureSyncComponents() {
        if (networkMonitor == null) {
            networkMonitor = new NetworkMonitor(requireContext());
            networkMonitor.start();
        }
        if (syncManager == null) {
            syncManager = new SyncManager(new MockSyncApi(), networkMonitor);
            syncManager.getSyncState().observe(getViewLifecycleOwner(), this::renderSyncState);
        }
    }

    private void renderSyncState(SyncState state) {
        if (binding == null || state == null) return;
        binding.tvSyncStatus.setText(getString(R.string.profile_sync_status_format, state.getMessage()));
        switch (state.getStatus()) {
            case RUNNING:
                binding.progressSync.setVisibility(View.VISIBLE);
                int total = state.getTotal() == 0 ? 1 : state.getTotal();
                int progress = (int) ((state.getProcessed() / (float) total) * 100);
                binding.progressSync.setProgress(progress);
                break;
            case SUCCESS:
            case ERROR:
            case OFFLINE:
            case IDLE:
                binding.progressSync.setVisibility(View.GONE);
                break;
        }
    }

    private void renderUser(User user) {
        if (user == null || binding == null) {
            return;
        }
        binding.tvName.setText(user.getName());
        binding.tvRole.setText(getString(R.string.profile_role_format,
                user.getRole() != null ? user.getRole() : getString(R.string.profile_unknown)));
        binding.tvUserId.setText(getString(R.string.profile_user_id_format, user.getUserId()));
        binding.tvPhone.setText(getString(R.string.profile_phone_format,
                user.getPhone() != null ? user.getPhone() : getString(R.string.profile_placeholder_dash)));
        binding.tvEmail.setText(getString(R.string.profile_email_format,
                user.getEmail() != null ? user.getEmail() : getString(R.string.profile_placeholder_dash)));
        binding.tvBio.setText(getString(R.string.profile_bio_format,
                user.getBio() != null ? user.getBio() : getString(R.string.profile_placeholder_dash)));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (networkMonitor != null) {
            networkMonitor.stop();
            networkMonitor = null;
        }
        binding = null;
    }
}

