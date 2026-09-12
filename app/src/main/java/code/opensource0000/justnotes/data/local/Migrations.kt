package code.opensource0000.justnotes.data.local

import androidx.room.migration.Migration

// Every schema migration this database has ever needed, in order.
//
// Empty, because version 1 has not been superseded yet. It exists anyway so
// that the first migration has an obvious place to go and an obvious way to
// reach production: JustNotesDatabase passes this array to the builder, and
// MigrationTest runs every entry in it against a database rebuilt from the
// exported schema.
//
// Adding a migration means three things, and forgetting any one of them is a
// crash on launch for everyone who already installed the app:
//   1. bump `version` on @Database
//   2. add the Migration here
//   3. let the build write the new app/schemas/<n>.json, and commit it
//
// MigrationTest catches the first two. Nothing catches the third but reading
// the diff, which is why the schema files are committed rather than ignored.
val ALL_MIGRATIONS: Array<Migration> = emptyArray()
