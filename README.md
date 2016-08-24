お気軽出費メモアプリ
====

# Description #

外出時にレシートが出ない場合など、出費したことをメモするために利用できるアプリである。
このアプリの機能として以下がある。

* 出費のメモ追加機能(店名、時刻、商品情報(名前と価格))
* 追加した店名からの履歴機能 (位置情報と紐付け、近い順に補完を表示)
* 履歴店名の編集機能 (店名の編集、Google Places APIを利用した位置情報の編集)
* すぐ起動できるように常にNotificationに表示する機能
* Google Spreadsheetに同期する機能
* 自動同期(5分間隔、15分、...)

# Requirement #

このアプリを利用するためには、Google API Consoleから以下のAPIを有効にする必要がある。

* Google Drive API
* Google Sheets API
* Google Places API for Android
