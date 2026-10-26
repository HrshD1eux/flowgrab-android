package hrshd1eux.flowgrab.util

import androidx.annotation.CheckResult

object SponsorUtil {
    private const val TAG = "SponsorUtil"
    private val emptySponsorData =
        SponsorData(Data(Viewer(SponsorshipsAsMaintainer(emptyList()))))

    @CheckResult
    fun getSponsors(): Result<SponsorData> = Result.success(emptySponsorData)
}
