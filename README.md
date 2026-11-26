# 부동산 투기 보드게임

### 실행 환경

* Java 25
* Paper 26.3 (build 26)

### 빌드 및 서버 설치

1. 빌드 시 Gradle composite build가 `Speculation`의 형제 폴더 `Kolor`를 참조하므로 두 소스 폴더를 나란히 둔 Java 25 환경에서 `.\gradlew.bat build` 실행
2. Kolor 프레임워크를 먼저 Paper 26.3 서버의 `plugins` 폴더에 설치
3. `build/libs/Speculation.jar`를 같은 서버의 `plugins` 폴더에 복사
4. 서버를 시작한 뒤 게임에서 팀 선택 `/team join <team> <player>`
5. 게임 시작 `/speculation start <world> <players> <teamMatch>`
    * 예) 개인매치 - `/speculation start world @a false`
    * 예) 그룹매치 - `/speculation start world @a true`

### 포함된 서버 실행

`run` 폴더에는 Paper 26.3 build 26과 Speculation 및 Kolor 플러그인이 포함되어 있습니다. Java 25를 설치한 뒤 `run` 폴더에서 Windows는 `run-windows.bat`, Linux는 `bash run-linux.sh`로 시작합니다. 최초 실행 시 생성되는 `eula.txt`의 내용을 확인하고 동의한 경우에만 `eula=true`로 설정한 뒤 다시 시작하세요. 기존 월드는 새 버전에서 변환될 수 있으므로 실행 전에 백업하세요.

### 리소스팩 배포

1. `.\gradlew.bat resourcepacksZip`을 실행해 `build/libs/resourcepack.zip`을 생성합니다. 명령 출력에 SHA-1 값이 표시됩니다.
2. ZIP 파일을 공개 HTTPS 주소에 올리고 서버에서 해당 주소를 받을 수 있는지 확인합니다.
3. 서버를 한 번 실행한 뒤 생성된 `plugins/Speculation/config.yml`의 `resource-pack.url`에 ZIP 주소, `resource-pack.sha1`에 빌드 출력의 SHA-1 값을 설정합니다.
4. 서버를 재시작한 뒤 `/speculation resourcepack`으로 적용합니다.

- 선택) 리소스팩 - `/speculation resourcepack`
  - 로컬에서 리소스팩을 미리 적용 시키지 않았다면 서버 접속 이전에 "서버 리소스팩" 설정을 허용으로 변경하시는 것을 권장합니다.

### Kolor 및 설정

- Speculation은 Kolor 프레임워크에 의존하므로 Kolor 플러그인을 함께 설치해야 합니다. Kolor가 없으면 서버에서 Speculation을 로드하지 않습니다.
- Kolor 호환성 계층에는 Tap의 패킷 전용 가짜 ArmorStand/아이템 기능이 없으므로, 게임 말과 효과 아이템은 추적·정리되는 비영속 Bukkit 엔티티로 표시됩니다. 이 엔티티들은 서버의 다른 플러그인에도 보일 수 있습니다.
- 게임 로직의 코루틴 실행·지연은 Kolor 전역 스케줄러를 사용합니다. `delay`는 50ms 단위의 서버 틱으로 올림 처리되어 서버가 느려지면 실제 대기 시간도 길어집니다.
- `speculation-core`는 독립적인 게임 규칙 계층으로 유지하고, Paper/Kolor 연동은 `speculation-plugin`에서 처리합니다. 부동 텍스트와 보드 부동산 표시는 Kolor `KolorDisplay`를 사용하며, 게임 말의 ArmorStand와 ItemFrame처럼 Kolor 표시 API가 다루지 않는 게임 엔티티는 Paper API를 사용합니다.
- Kolor 자체는 Folia 스케줄러를 지원하지만, 이 게임 플러그인의 보드/게임 루프는 아직 전역 Paper 실행 모델을 사용합니다. Folia 호환 플러그인이라고 선언하거나 보장하지 않습니다.
- `config.yml`에서 명령어 권한, 시작 대기 시간, 시작 잔액/보상, 주사위 수, 감옥 턴, 카지노 베팅, 부동산 통행료, 보드 위치/크기, 리소스팩 및 보드 표시 이름/모델 ID를 설정할 수 있습니다.
- 부동산 표시 설정은 보드 순서대로 23개, 특수 칸 표시는 9개 항목을 유지해야 합니다. 값이 잘못되거나 항목 수가 맞지 않으면 플러그인이 잘못된 설정을 명시하고 로드를 중단합니다.
- 설정 변경은 서버 재시작 후 적용됩니다.

--- 

#### 지원하는 팀 색상 [사각형은 그룹매치 색상]

* ⬜ white
* ⬜ gray
* 🟥 red
* 🟥 dark_red
* 🟨 yellow
* 🟨 gold
* 🟩 green
* 🟩 dark_green
* 🟦 aqua
* 🟦 dark_aqua
* 🟪 light_purple
* 🟪 dark_purple

---

### 소스코드 수정이라는 끔찍한 도전을 하시는 분들을 위하여

이 프로젝트는 두개의 레이어로 분리되어 있습니다.

1. `speculation-core` = 실제 게임 실행 레이어
2. `speculation-plugin` = 진행 상황을 마인크래프트 서버(paper)에 보여주는 레이어

이 두 레이어가 상호작용하며 게임이 실행됩니다.

대부분의 진행은 `speculation-core` 에서 이뤄지며 플레이어의 응답이 필요한 경우

`speculation-plugin`에서 dialog를 수신해 플레이어의 응답을 전달합니다.

--- 

#### 참고

1. 모든 진행은 처음 사용해본 코루틴으로 작성했습니다.
2. 방송 컨텐츠를 한정한 코드를 작성하다보니 설정 대부분이 하드코딩되어 있습니다.

---

> ### 제작자
> * ✔️기본 건축물 - `adfgdfg` `✔️monun`
> * 시작 - `jung27`
> * ✔️감옥 - `✔️kinglee2005`
> * ✔️포탈 - `✔️degree2121`
> * ✔️축제 - `✔️coroskai`
> * ✔️국세청 - `✔️piese1028`
> * ✔️카지노 - `✔️piese1028`
> * ✔️이벤트카드 - `✔️degree2121`
> * ✔️봉화(10100) - `✔️monun`
> * ✔️영덕(12600) - `✔️adfgdfg` `eso136` `jangj` `mdjoon` `tbacaf`
> * ✔️의성(12400) - `✔️adfgdfg` `alexjang`
> * ✔️임실(11400) - `✔️app6460`
> * 예천(12500) - `pingu3628`
> * ✔️안동(10000) - `✔️adfgdfg` `degree2121`
> * ✔️평창(11900) - `implan` `irving_braxiatel` `✔️jhhan611` `piggy3590`
> * 보령(10200) - `traloc_dheckoa`
> * ✔️횡성(11300) - `adfgdfg` `✔️sgkill6`
> * ✔️강화(10900) - `✔️hgnb0508`
> * ✔️태백(12300) - `✔️adfgdfg` `degree2121`
> * ✔️동해(10800) - `✔️zlfn`
> * ✔️나주(11700) - `✔️degree2121` `moon0907` `pingu3628`
> * ✔️춘천(10500) - `information` `✔️pumkins2` `sssndw10` `tenthdoctor`
> * ✔️경주(11200) - `✔️adfgdfg` `jjoa` `jw`
> * ✔️포항(11800) - `✔️adfgdfg` `dytro`
> * ✔️속초(12200) - `chojinwoo` `✔️coroskai`
> * ✔️당진(10700) - `kde0607` `✔️piese1028` `pingu3628`
> * ✔️제주(11600) - `✔️menil`
> * ✔️김포(11000) - `✔️pparru`
> * ✔️창원(10400) - `✔️ikmyung`
> * ✔️세종(12000) - `✔️adfgdfg` `degree2121`
> * ✔️인천(11500) - `✔️degree2121` `ikohistory`
> * 광주(11100) - `adfgdfg`
> * ✔️대전(10600) - `cheonanstn` `✔️jjoa` `piese1028`
> * ✔️부산(10300) - `ghkdgnl` `✔️piese1028`
> * ✔️서울(12100) - `✔️megat88` `obinox`

---
[![GitHub Sponsors](https://img.shields.io/github/sponsors/Shai0717?style=flat&logo=githubsponsors&color=ea4aaa)](https://github.com/sponsors/Shai0717)
