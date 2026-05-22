package os.kei.ui.page.main.ba

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import os.kei.ui.page.main.ba.support.BaPageSnapshot

internal class BaOfficeViewModel(
    application: Application,
) : AndroidViewModel(application) {
    val initialSnapshot: BaPageSnapshot = BaOfficeRepository.loadSnapshot()
    val office: BaOfficeController = BaOfficeController(initialSnapshot)
}
