package com.home.domain;

import com.home.annotation.Column;
import com.home.annotation.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Table(name = "hit")
@ToString
@Getter
@Setter
public class Hit {
    @Column(name = "id")
    private Long id;

    @Column(name = "sound")
    private String sound;
}
