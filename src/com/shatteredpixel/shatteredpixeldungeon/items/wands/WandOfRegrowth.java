/*
 * Pixel Dungeon
 * Copyright (C) 2012-2014  Oleg Dolya
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */
package com.shatteredpixel.shatteredpixeldungeon.items.wands;

import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.items.Dewdrop;
import com.shatteredpixel.shatteredpixeldungeon.items.Generator;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MagesStaff;
import com.shatteredpixel.shatteredpixeldungeon.plants.Plant;
import com.shatteredpixel.shatteredpixeldungeon.plants.Starflower;
import com.shatteredpixel.shatteredpixeldungeon.plants.Sungrass;
import com.shatteredpixel.shatteredpixeldungeon.sprites.ItemSpriteSheet;
import com.watabou.noosa.audio.Sample;
import com.shatteredpixel.shatteredpixeldungeon.Assets;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Blob;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Regrowth;
import com.shatteredpixel.shatteredpixeldungeon.effects.MagicMissile;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;
import com.watabou.utils.Callback;
import com.watabou.utils.Random;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

public class WandOfRegrowth extends Wand {

	{
		name = "Wand of Regrowth";
		image = ItemSpriteSheet.WAND_REGROWTH;

		collisionProperties = Ballistica.STOP_TERRAIN;
	}

	//the actual affected cells
	private HashSet<Integer> affectedCells;
	//the cells to trace growth particles to, for visual effects.
	private HashSet<Integer> visualCells;
	private int direction = 0;
	
	@Override
	protected void onZap( Ballistica bolt ) {

		//ignore tiles which can't have anything grow in them.
		for (Iterator<Integer> i = affectedCells.iterator(); i.hasNext();) {
			int c = Dungeon.level.map[i.next()];
			if (!(c == Terrain.EMPTY ||
					c == Terrain.EMBERS ||
					c == Terrain.EMPTY_DECO ||
					c == Terrain.GRASS ||
					c == Terrain.HIGH_GRASS)) {
				i.remove();
			}
		}

		float numPlants, numDews, numPods, numStars;

		int chrgUsed = chargesPerCast();
		//numbers greater than n*100% means n garunteed plants, e.g. 210% = 2 plants w/10% chance for 3 plants.
		numPlants = 0.2f + chrgUsed*chrgUsed*0.020f; //scales from 22% to 220%
		numDews = 0.05f + chrgUsed*chrgUsed*0.016f; //scales from 6.6% to 165%
		numPods = 0.02f + chrgUsed*chrgUsed*0.013f; //scales from 3.3% to 135%
		numStars = (chrgUsed*chrgUsed*chrgUsed/5f)*0.005f; //scales from 0.1% to 100%
		placePlants(numPlants, numDews, numPods, numStars);

		for (int i : affectedCells){
			int c = Dungeon.level.map[i];
			if (c == Terrain.EMPTY ||
					c == Terrain.EMBERS ||
					c == Terrain.EMPTY_DECO) {
				Level.set( i, Terrain.GRASS );
			}

			GameScene.add( Blob.seed( i, 10, Regrowth.class ) );

		}
	}

	private void spreadRegrowth(int cell, float strength){
		if (strength >= 0 && Level.passable[cell] && !Level.losBlocking[cell]){
			affectedCells.add(cell);
			if (strength >= 1.5f) {
				spreadRegrowth(cell + Level.NEIGHBOURS8[left(direction)], strength - 1.5f);
				spreadRegrowth(cell + Level.NEIGHBOURS8[direction], strength - 1.5f);
				spreadRegrowth(cell + Level.NEIGHBOURS8[right(direction)], strength-1.5f);
			} else {
				visualCells.add(cell);
			}
		} else if (!Level.passable[cell] || Level.losBlocking[cell])
			visualCells.add(cell);
	}

	private void placePlants(float numPlants, float numDews, float numPods, float numStars){
		Iterator<Integer> cells = affectedCells.iterator();
		Level floor = Dungeon.level;

		while(cells.hasNext() && Random.Float() <= numPlants){
			floor.plant((Plant.Seed) Generator.random(Generator.Category.SEED), cells.next());
			numPlants --;
		}

		while (cells.hasNext() && Random.Float() <= numDews){
			floor.plant(new Dewcatcher.Seed(), cells.next());
			numDews --;
		}

		while (cells.hasNext() && Random.Float() <= numPods){
			floor.plant(new Seedpod.Seed(), cells.next());
			numPods --;
		}

		while (cells.hasNext() && Random.Float() <= numStars){
			floor.plant(new Starflower.Seed(), cells.next());
			numStars --;
		}

	}

	private int left(int direction){
		return direction == 0 ? 7 : direction-1;
	}

	private int right(int direction){
		return direction == 7 ? 0 : direction+1;
	}

	@Override
	public void onHit(MagesStaff staff, Char attacker, Char defender, int damage) {
		//like vampiric enchantment, except with herbal healing buff

		int level = Math.max( 0, 0/*staff level*/ );

		// lvl 0 - 33%
		// lvl 1 - 43%
		// lvl 2 - 50%
		int maxValue = damage * (level + 2) / (level + 6);
		int effValue = Math.min( Random.IntRange(0, maxValue), attacker.HT - attacker.HP );

		Buff.affect(attacker, Sungrass.Health.class).boost( effValue );

	}

	protected void fx( Ballistica bolt, Callback callback ) {

		affectedCells = new HashSet<>();
		visualCells = new HashSet<>();

		int maxDist = Math.round(1.2f + chargesPerCast()*.8f);
		int dist = Math.min(bolt.dist, maxDist);

		for (int i = 0; i < Level.NEIGHBOURS8.length; i++){
			if (bolt.sourcePos+Level.NEIGHBOURS8[i] == bolt.path.get(1)){
				direction = i;
				break;
			}
		}

		float strength = maxDist;
		for (int c : bolt.subPath(1, dist)) {
			strength--; //as we start at dist 1, not 0.
			if (!Level.losBlocking[c]) {
				affectedCells.add(c);
				spreadRegrowth(c + Level.NEIGHBOURS8[left(direction)], strength - 1);
				spreadRegrowth(c + Level.NEIGHBOURS8[direction], strength - 1);
				spreadRegrowth(c + Level.NEIGHBOURS8[right(direction)], strength - 1);
			} else {
				visualCells.add(c);
			}
		}

		//going to call this one manually
		visualCells.remove(bolt.path.get(dist));

		for (int cell : visualCells){
			//this way we only get the cells at the tip, much better performance.
			MagicMissile.foliage(curUser.sprite.parent, bolt.sourcePos, cell, null);
		}
		MagicMissile.foliage( curUser.sprite.parent, bolt.sourcePos, bolt.path.get(dist), callback );

		Sample.INSTANCE.play( Assets.SND_ZAP );
	}

	@Override
	//consumes all available charges, needs at least one.
	protected int chargesPerCast() {
		return Math.max(1, curCharges);
	}

	@Override
	public String desc() {
		return
				"This wand is made from a thin shaft of expertly carved wood. " +
				"Somehow it is still alive and vibrant, bright green like a young tree's core.\n" +
				"\n" +
				"When used, this wand will consume all its charges to blast magical regrowth energy outward " +
				"in a cone. This magic will cause grass, roots, and rare plants to spring to life.\n" +
				"\n" +
				"\"When life ceases new life always begins to grow... The eternal cycle always remains!\"";
	}


	public static class Dewcatcher extends Plant{

		{
			image = 12;
			plantName = "Dewcatcher";
		}

		@Override
		public void activate(Char ch) {
			super.activate( ch );

			int nDrops = Random.NormalIntRange(2, 8);

			ArrayList<Integer> candidates = new ArrayList<Integer>();
			for (int i : Level.NEIGHBOURS8){
				if (Level.passable[pos+i]){
					candidates.add(pos+i);
				}
			}

			for (int i = 0; i < nDrops && !candidates.isEmpty(); i++){
				Integer c = Random.element(candidates);
				Dungeon.level.drop(new Dewdrop(), c).sprite.drop(pos);
				candidates.remove(c);
			}

		}

		@Override
		public String desc() {
			return "";
		}

		//seed is never dropped, only care about plant class
		public static class Seed extends Plant.Seed {
			{
				plantClass = Dewcatcher.class;
			}
		}
	}

	public static class Seedpod extends Plant{

		{
			image = 13;
			plantName = "Seed Pod";
		}

		@Override
		public void activate(Char ch) {
			super.activate( ch );

			int nSeeds = Random.NormalIntRange(1, 5);

			ArrayList<Integer> candidates = new ArrayList<Integer>();
			for (int i : Level.NEIGHBOURS8){
				if (Level.passable[pos+i]){
					candidates.add(pos+i);
				}
			}

			for (int i = 0; i < nSeeds && !candidates.isEmpty(); i++){
				Integer c = Random.element(candidates);
				Dungeon.level.drop(Generator.random(Generator.Category.SEED), c).sprite.drop(pos);
				candidates.remove(c);
			}

		}

		@Override
		public String desc() {
			return "";
		}

		//seed is never dropped, only care about plant class
		public static class Seed extends Plant.Seed {
			{
				plantClass = Seedpod.class;
			}
		}

	}

}
