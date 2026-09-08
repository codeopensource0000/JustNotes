package code.opensource0000.justnotes.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import code.opensource0000.justnotes.R

// The catch-all folder is seeded into the database once, at first launch, with
// a fixed name. That name is therefore frozen in whatever language the app
// happened to be built with — an English user would have read "Non classé"
// forever, and changing the constant later would not touch databases already
// created.
//
// So the stored name is treated as an internal marker and never shown: every
// place that displays a folder resolves it through here, and the default one
// gets a localised label that follows the app language like everything else.
@Composable
fun folderLabel(name: String, isDefault: Boolean): String =
    if (isDefault) stringResource(R.string.folder_default_name) else name
