package com.automattic.simplenote.utils;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.Observer;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.automattic.simplenote.Simplenote;
import com.automattic.simplenote.models.Account;
import com.simperium.client.Bucket;
import com.simperium.client.BucketObjectMissingException;

import static com.automattic.simplenote.models.Account.KEY_EMAIL_VERIFICATION;

public class ReviewAccountVerifier {
    private final Simplenote simplenote;

    public ReviewAccountVerifier(Simplenote simplenote) {
        this.simplenote = simplenote;
    }

    public void startJob() {
        // Enqueue the worker to check when the account has synced
        final OneTimeWorkRequest accountCheckerWork = new OneTimeWorkRequest.Builder(AccountCheckerWorker.class).build();
        WorkManager.getInstance(simplenote).enqueue(accountCheckerWork);

        // Observe for results from the worker
        final WorkManager workManager = WorkManager.getInstance(simplenote);
        workManager.getWorkInfoByIdLiveData(accountCheckerWork.getId())
                .observeForever(new Observer<WorkInfo>() {
                    @Override
                    public void onChanged(WorkInfo workInfo) {
                        if (workInfo.getState() == WorkInfo.State.SUCCEEDED) {
                            boolean hasVerifiedEmail = workInfo.getOutputData()
                                    .getBoolean(AccountCheckerWorker.KEY_RESULT_HAS_VERIFIED_EMAIL, false);
                            if (!hasVerifiedEmail) {
                                boolean hasSentEmail = workInfo.getOutputData()
                                        .getBoolean(AccountCheckerWorker.KEY_RESULT_HAS_SENT_EMAIL, true);
                                simplenote.showReviewVerifyAccount(hasSentEmail);
                            }
                        }

                        workManager.getWorkInfoByIdLiveData(accountCheckerWork.getId()).removeObserver(this);
                    }
                });
    }

    public static class AccountCheckerWorker extends Worker {
        public static final String KEY_RESULT_HAS_VERIFIED_EMAIL = "result_has_verified_email";
        public static final String KEY_RESULT_HAS_SENT_EMAIL = "result_has_sent_email";


        public AccountCheckerWorker(
                @NonNull Context context,
                @NonNull WorkerParameters params) {
            super(context, params);

        }

        @NonNull
        @Override
        public Result doWork() {
            Simplenote simplenote = (Simplenote) getApplicationContext();

            Bucket<Account> accountBucket = simplenote.getAccountBucket();
            // Loops until the account is synchronized
            Account account = null;
            while (account == null) {
                try {
                    account = accountBucket.get(KEY_EMAIL_VERIFICATION);
                } catch (BucketObjectMissingException e) {
                    // Do nothing here
                }
            }

            String email = simplenote.getUserEmail();
            boolean hasVerifiedEmail = account.hasVerifiedEmail(email);
            boolean hasSentEmail = account.hasSentEmail(email);
            Data result = new Data.Builder()
                    .putBoolean(KEY_RESULT_HAS_VERIFIED_EMAIL, hasVerifiedEmail)
                    .putBoolean(KEY_RESULT_HAS_SENT_EMAIL, hasSentEmail)
                    .build();

            return Result.success(result);
        }
    }
}
