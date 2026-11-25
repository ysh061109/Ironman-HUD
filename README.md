# Iron HUD

NeoForge **1.21.1** 클라이언트 전용 모드 — 영화 속 파워슈트 HUD에서 영감을 받은 SF 헬멧 디스플레이.
서버에 설치할 필요가 없고, 일반/바닐라 서버에도 접속할 수 있습니다.

> 팬 프로젝트입니다. 영화사 및 원작 권리자와 무관하며, 로고·영상·음원·폰트 등 원본 자산을 일절 사용하지 않습니다.
> 효과음 10종은 전부 코드로 합성한 오리지널입니다 (`tools/gen_sounds.py`).

## 기능

| 모듈 | 설명 |
|---|---|
| 아크 리액터 | 체력·허기(전력)·방어력 3중 게이지, 체력 낮으면 적색 맥동, 산소 바 |
| **슈트 상태 도면** | 투구/흉갑/레깅스/부츠 내구도를 인체 도면에 색상으로 표시. 피격 시 해당 부위 점멸, 내구도 30% 미만 적색 경고, 파손 시 `ARMOR BREACH` |
| **파손 연출** | 큰 피해를 받으면 바이저에 균열 생성(시간이 지나면 사라짐), 체력 30% 이하에서는 균열이 유지되고 화면 가장자리가 붉게 맥동 |
| 경고 배너 | 위험도별(정보/주의/치명) 배너와 효과음: 선체 손상, 전력 부족, 산소 부족, 화염, 용암 근접, 적 근접, 크리퍼 점화, 갑옷 파손 등 |
| **충돌/추락 경고** | 겉날개 비행·낙하 중 진행 방향을 레이캐스트해 충돌까지 시간(TTC) 표시, 낙하 피해 예측, 치명적이면 `FATAL IMPACT` |
| 인공 수평선 | 비행 중 피치 래더 + 비행경로 마커 (설정으로 상시 표시 가능) |
| 나침반 | 방위 테이프, 주변 적 위치 틱, 웨이포인트 마커 |
| 레이더 | 평면 지도: 적(적색)/플레이어/중립 개체, 스윕 애니메이션, 웨이포인트 |
| **시네마틱 타깃 스캔** | 바라본 개체에 회전 링 스택이 달라붙고(체력 호 포함), 와이어프레임 박스 + 위아래로 훑는 스캔 평면, 리더 라인에서 마크 정보가 타이핑되듯 출력: 종류/분류/체력/방어/공격력/속도/거리/좌표/크기/상태이상/손에 든 아이템/위협도. 처치 시 `TARGET ELIMINATED` |
| 월드 마커 | 적대 몹 위에 삼각 마커 + 거리, 웨이포인트 3D 마커 |
| 광학 줌 | 키 누르고 휠로 배율 조절, 줌 전용 레티클 |
| 광역 스캔 | 화면 전체로 퍼지는 원형 펄스, 펄스가 닿는 개체마다 회전 링 + 이름·거리·체력바 |
| 고도 그래프 | 최근 고도 변화 트레이스, 상승/하강 속도, 지면까지 거리(AGL) |
| 효과음 | 부팅, 종료, 주의, 치명 경고(클랙슨), 갑옷 파손, 락온, 처치, 충돌, 줌, 핑 |

## 조작 (조작 설정 > "Iron HUD"에서 변경 가능)

| 키 | 동작 |
|---|---|
| `H` | 슈트 HUD 켜기/끄기 — 끄면 바이저 셔터가 닫히며 마크 기본 UI(체력·배고픔·핫바·인벤토리)로 복귀, 켜면 부팅 시퀀스 재생 (음성: "HUD off/on") |
| `Z` (누르고 있기) | 광학 줌 — 마우스 휠로 배율 조절 |
| `K` | 현재 위치에 웨이포인트 설정 (6블록 이내에서 다시 누르면 해제) |
| `X` | 광역 스캔 펄스 |

설정은 모드 목록 > Iron HUD > 설정 화면 또는 `config/ironhud-client.toml`에서 바꿀 수 있습니다
(색상 테마, 효과음/볼륨, 각 모듈 표시, 락온/레이더 거리, 최대 줌 등).


## Modus (수트 AI)

호출어 **"Modus"**. 음성으로 부르거나, 전용 HUD 챗(`M`)에 입력해서 부릅니다. 영어/한국어 지원 (`/modus lang en|ko`, 설정의 `voiceLang`).

- **음성 출력**: OS 음성엔진으로 합성 → 헬멧 무전 필터 → 재생. Windows(SAPI), macOS(`say`), Linux(`espeak-ng`) 자동 선택.
  더 좋은 목소리는 설정의 `ttsCommand`에 Piper 같은 명령을 지정하세요.
- **선제 음성 경고**: 선체 손상, 갑옷 파손, 충돌/추락, 산소, 용암, 크리퍼 점화, 적 근접, 목표 제거, 스캔 결과 등.
- **음성 호출**: 오프라인 인식(Vosk). 처음 한 번 `/modus install` (라이브러리+모델 다운로드, 약 50~100MB) 후
  `/modus mic on` 또는 `V` 푸시투토크. 마이크는 기본 꺼짐이며, 켜기 전에는 아무것도 듣지 않습니다.
- **명령 예시**: "Modus, status report" / "scan" / "zoom in" / "mark waypoint" / "power to sensors" / "lock targets" / "run diagnostics" /
  "where am I" / "weather" — 한국어: "모더스, 상태 보고" / "스캔" / "확대" / "웨이포인트" / "센서에 전력" / "락온" / "진단" / "위치" / "날씨"
- **신경망 목소리**: `/modus install`(또는 `/modus voice install`)이 [Piper](https://github.com/rhasspy/piper) 엔진과 음성 모델(영어: 영국 남성 `en_GB-alan-medium`, 한국어: `ko_KR-kss-medium`)을 `config/modus/piper`로 내려받아 컴퓨터 기본 TTS 대신 사용합니다. 받기에 실패하거나 실행이 안 되면 자동으로 시스템 음성으로 돌아갑니다. 영화 속 자비스(폴 베타니) 목소리 그대로는 아니며, 다른 음성은 `piperVoiceUrl`로 교체 가능합니다.
- 명령: `/modus say|ask|install|voice install|mic on|off|lang|test|status`

## 추가 HUD 모듈

3D 지형 와이어프레임 지도, 무장/탄약 패널(`R`: 전술 락온 + 우선순위 목록), 전력 배분(`P`: 센서/광학/분석이 실제 범위·줌·락 수에 영향),
헬멧 닫힘 부팅 시퀀스와 바이저 프레임, 피격 방향 표시, 시스템 로그, 다른 플레이어 채팅 시 영상통화 창(얼굴+메시지), 진단 표(`G`).

| 키 | 동작 |
|---|---|
| `M` | Modus 전용 HUD 챗 |
| `V` | 푸시투토크 |
| `R` | 전술 락온: 주변 스캔 애니메이션 후, 범위 안의 모든 적이 계속 표시됨(우선순위 목록 + 와이어프레임 카드). 다시 누르면 해제. 바라보는 것만으로는 적 정보가 뜨지 않음 |
| `P` | 전력 배분 전환 |
| `G` | 시스템 진단 표 |

## 설치

[Releases](../../releases) 또는 Actions 아티팩트의 `ironhud-*.jar`를 `mods` 폴더에 넣으세요.
필요: Minecraft 1.21.1 + NeoForge 21.1.x

## 빌드

JDK 21, Gradle 8.8+ 필요.

```
gradle build          # build/libs/ironhud-1.0.0.jar
gradle runClient      # 개발용 클라이언트
```

`main`에 push하면 GitHub Actions가 자동으로 빌드하고, `v*` 태그를 push하면 Release에 jar를 첨부합니다.

## 효과음 다시 만들기

```
python3 tools/gen_sounds.py    # numpy + ffmpeg(libvorbis) 필요
```

## 영화 HUD 참고 자료

기능 구성은 영화 속 HUD를 분석한 공개 글을 참고했습니다 (기능 목록만 참고, 자산 사용 없음):

- [Sci-fi Interfaces — Iron Man HUD: Just the functions](https://scifiinterfaces.com/2015/07/13/iron-man-hud-just-the-functions/)
- [AWN — Graphic UIs in Avengers: Age of Ultron](https://www.awn.com/vfxworld/keeping-it-user-friendly-graphic-uis-avengers-age-ultron)
- [Creative Bloq — The Cinema 4D secrets of Iron Man's interface](https://www.creativebloq.com/3d/cinema-4d-secrets-iron-mans-interface-61515419)

## 라이선스

MIT

## 화면 배치와 설정
- 왼쪽: 로그 → 선체 경고등(실제 HP 기준, 50% 이하 호박색, 30% 이하 적색 점멸) → 슈트 상태 → 리액터. 아래 가운데: 슈트 핫바(실제 슬롯과 연동), 아이콘 5개. 오른쪽: 정보, 지형 지도, 레이더.
- 지도 모드(Xaero/JourneyMap 등)가 있으면 오른쪽 위를 자동으로 비웁니다. `topRightReserve`로 직접 조정하세요.
- 렉이 심하면 설정의 `lowDetail`을 켜세요. FPS가 40 아래면 자동으로 단순 모드로 바뀝니다.
- `customHotbar`, `skinScreens`로 핫바 교체와 인벤토리 스킨을 끌 수 있습니다.

## 슈트 홀로그램 · 지형 스캔 · 효과음 (추가)
- 좌측 SUIT STATUS는 마크 플레이어 비율(32×16)의 3D 와이어프레임 아이언맨 슈트가 회전하는 홀로그램입니다. 헬멧/흉갑/다리/부츠 내구도에 따라 색이 바뀌고(파손 시 깜빡임·선 끊김), 부팅 때 발끝부터 생성되며 스캔 평면이 위로 올라갑니다. 아크 리액터, 손바닥·발 리펄서, 비행 시 추진 불꽃과 홀로 패드 포함.
- 지형 스캔(`X`/`R`): 스캔 시작 시 주변 지형을 저장해 실제 지형 위에 와이어 메시가 파동처럼 퍼지며 드러나고(파면 링이 지형을 따라감, 지나간 자리에 기둥 펄스, 이후 은은히 남았다가 사라짐), 파동이 닿는 순간 적/개체 블립음이 납니다. 우측 지형 패널도 같은 파동으로 안쪽부터 나타납니다.
- 새 효과음: engage(슈트 착용), scan_sweep, sonar, blip, servo(줌), swish(패널), lock_engage, holo(홀로그램 생성), terrain_pulse, acquire. 모두 `tools/gen_sounds.py`로 합성한 자체 제작 음원입니다.
